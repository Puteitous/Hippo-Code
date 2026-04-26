package com.example.agent.context.compressor;

import com.example.agent.application.ConversationService;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CompactForkExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CompactForkExecutor.class);
    
    private static final int COMPACT_TIMEOUT_SECONDS = 120;
    
    private final LlmClient llmClient;
    private final ConversationService conversationService;
    private final TokenEstimator tokenEstimator;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    
    private Consumer<String> partialResultListener;
    
    public CompactForkExecutor() {
        this.llmClient = ServiceLocator.get(LlmClient.class);
        this.conversationService = ServiceLocator.get(ConversationService.class);
        this.tokenEstimator = ServiceLocator.get(TokenEstimator.class);
    }
    
    public CompactForkExecutor(LlmClient llmClient, ConversationService conversationService, 
                               TokenEstimator tokenEstimator) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
        this.tokenEstimator = tokenEstimator;
    }
    
    public void setPartialResultListener(Consumer<String> listener) {
        this.partialResultListener = listener;
    }
    
    public CompactResult executeForkedCompaction(String parentSessionId, String compactionPrompt) {
        return executeForkedCompaction(parentSessionId, compactionPrompt, COMPACT_TIMEOUT_SECONDS);
    }
    
    public CompactResult executeForkedCompaction(String parentSessionId, String compactionPrompt, int timeoutSeconds) {
        if (parentSessionId == null || parentSessionId.isEmpty()) {
            logger.warn("父会话ID为空，回退到直接执行模式");
            return executeDirect(compactionPrompt);
        }
        
        Conversation parent = conversationService.getConversation(parentSessionId);
        if (parent == null) {
            logger.warn("父会话不存在，回退到直接执行模式: {}", parentSessionId);
            return executeDirect(compactionPrompt);
        }
        
        int parentTokenCount = tokenEstimator.estimate(parent.getMessages());
        
        Conversation forked = conversationService.forkConversation(parentSessionId, 
            buildCompactionFinalInstruction(compactionPrompt));
        
        logger.info("✅ Fork 压缩启动: 父会话消息={}条, {} tokens, Cache 命中比例 ~{}%",
            parent.getMessages().size(),
            parentTokenCount,
            Math.round(100.0 * parent.getMessages().size() / (parent.getMessages().size() + 1)));
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<CompactResult> future = executor.submit(() -> {
            try {
                List<Message> context = conversationService.prepareForInference(forked);
                
                ChatResponse response = llmClient.chat(
                    context,
                    Collections.emptyList()
                );
                
                String summary = extractSummaryContent(response);
                int outputTokens = tokenEstimator.estimateTextTokens(summary);
                
                logger.info("✅ Fork 压缩完成: 输出 {} tokens, 输入缓存命中 ~{}%",
                    outputTokens,
                    Math.round(100.0 * (parentTokenCount - 100) / parentTokenCount));
                
                return CompactResult.success(summary, true, outputTokens);
                
            } catch (Exception e) {
                logger.error("❌ Fork 压缩失败，回退到直接执行: {}", e.getMessage());
                throw new CancellationException("Fork 压缩失败");
            }
        });
        
        try {
            CompactResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            executor.shutdown();
            return result;
        } catch (TimeoutException e) {
            logger.warn("⏱️ Fork 压缩超时 ({}秒)，回退到直接执行", timeoutSeconds);
            future.cancel(true);
            executor.shutdownNow();
            return executeDirect(compactionPrompt);
        } catch (ExecutionException | InterruptedException e) {
            logger.warn("⚠️ Fork 压缩执行异常，回退到直接执行: {}", e.getMessage());
            future.cancel(true);
            executor.shutdownNow();
            return executeDirect(compactionPrompt);
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }
    
    private CompactResult executeDirect(String compactionPrompt) {
        if (cancelled.get()) {
            return CompactResult.cancelled();
        }
        
        try {
            String fullPrompt = buildCompactionFinalInstruction(compactionPrompt);
            List<Message> messages = Collections.singletonList(Message.user(fullPrompt));
            
            ChatResponse response = llmClient.chat(
                messages,
                Collections.emptyList()
            );
            
            String summary = extractSummaryContent(response);
            logger.info("✅ 直接压缩模式完成");
            
            return CompactResult.success(summary, false, 0);
        } catch (Exception e) {
            logger.error("❌ 直接压缩也失败: {}", e.getMessage());
            return CompactResult.failure(e.getMessage());
        }
    }
    
    private String buildCompactionFinalInstruction(String basePrompt) {
        return basePrompt + "\n\n" +
            "---\n" +
            "## 压缩模式特殊指令\n" +
            "- **禁止调用任何工具**：你现在处于上下文压缩模式，不需要任何工具\n" +
            "- **只输出摘要**：直接输出结构化摘要内容，不需要询问，不需要确认\n" +
            "- **不要编造信息**：基于已有对话内容进行摘要，不要添加外部信息\n" +
            "- **query_source=compact**：你正在执行压缩任务，不要触发任何新的压缩操作";
    }
    
    private String extractSummaryContent(ChatResponse response) {
        Message msg = response.getMessage();
        if (msg == null) {
            return "";
        }
        String content = msg.getContent() != null ? msg.getContent() : "";
        if (partialResultListener != null) {
            partialResultListener.accept(content);
        }
        return content;
    }
    
    public void cancel() {
        cancelled.set(true);
    }
    
    public static class CompactResult {
        private final boolean success;
        private final boolean usedFork;
        private final String summary;
        private final String error;
        private final int outputTokens;
        private final boolean cancelled;
        
        private CompactResult(boolean success, boolean usedFork, String summary, String error, 
                              int outputTokens, boolean cancelled) {
            this.success = success;
            this.usedFork = usedFork;
            this.summary = summary;
            this.error = error;
            this.outputTokens = outputTokens;
            this.cancelled = cancelled;
        }
        
        public static CompactResult success(String summary, boolean usedFork, int outputTokens) {
            return new CompactResult(true, usedFork, summary, null, outputTokens, false);
        }
        
        public static CompactResult failure(String error) {
            return new CompactResult(false, false, "", error, 0, false);
        }
        
        public static CompactResult cancelled() {
            return new CompactResult(false, false, "", "用户取消", 0, true);
        }
        
        public boolean isSuccess() { return success && !cancelled; }
        public boolean isUsedFork() { return usedFork; }
        public String getSummary() { return summary; }
        public String getError() { return error; }
        public int getOutputTokens() { return outputTokens; }
        public boolean isCancelled() { return cancelled; }
    }
}
