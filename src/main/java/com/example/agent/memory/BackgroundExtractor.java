package com.example.agent.memory;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BackgroundExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundExtractor.class);

    private static final int INITIAL_TOKEN_THRESHOLD = 10000;
    private static final int TOKEN_GROWTH_THRESHOLD = 5000;
    private static final int TOOL_CALL_THRESHOLD = 3;

    private final SessionMemoryManager memoryManager;
    private final SessionCompactionState compactionState;
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;

    private final AtomicInteger toolCallCountSinceLastExtraction = new AtomicInteger(0);
    private final AtomicBoolean extractionInProgress = new AtomicBoolean(false);
    private int lastExtractedTokenCount = 0;
    private String lastExtractedMessageId;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String SYSTEM_PROMPT = """
        ## 会话记忆提取任务

        请仔细阅读以下对话历史，按以下 3 个维度提取结构化记忆：

        ### 1. 关键决策
        - 架构选择和设计决策
        - 用户明确的偏好和要求
        - 约定的实现方案

        ### 2. 错误与修复
        - 遇到的坑和踩过的雷
        - 验证过的解决方案
        - 需要避免的反模式

        ### 3. 当前进度
        - 已完成的里程碑
        - 当前正在做什么
        - 下一步明确的计划

        ---

        要求：
        - 只保留真正重要的，不要记录临时调试信息
        - 使用 Markdown 列表，每条简洁
        - 相同/相似的内容合并去重
        - 输出只包含 3 个章节，不要其他解释
        """;

    public BackgroundExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(sessionId, tokenEstimator, llmClient, new SessionCompactionState());
    }

    public BackgroundExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient, SessionCompactionState compactionState) {
        this.memoryManager = new SessionMemoryManager(sessionId);
        this.compactionState = compactionState;
        this.tokenEstimator = tokenEstimator;
        this.llmClient = llmClient;
    }

    public void onMessageAdded(Message message, List<Message> fullConversation) {
        if (message.isTool() || message.getRole().equals("tool")) {
            toolCallCountSinceLastExtraction.incrementAndGet();
        }

        checkAndExtract(fullConversation);
    }

    public void checkAndExtract(List<Message> fullConversation) {
        if (!shouldExtract(fullConversation)) {
            return;
        }

        if (extractionInProgress.compareAndSet(false, true)) {
            EXECUTOR.submit(() -> {
                try {
                    performExtraction(fullConversation);
                } finally {
                    extractionInProgress.set(false);
                }
            });
        }
    }

    private boolean shouldExtract(List<Message> fullConversation) {
        if (extractionInProgress.get()) {
            return false;
        }

        int currentTokens = tokenEstimator.estimate(fullConversation);

        boolean hasReachedInitialThreshold = currentTokens >= INITIAL_TOKEN_THRESHOLD;
        if (!hasReachedInitialThreshold) {
            return false;
        }

        boolean isFirstExtraction = lastExtractedTokenCount == 0;
        boolean hasMetTokenGrowth = isFirstExtraction 
            ? true 
            : currentTokens - lastExtractedTokenCount >= TOKEN_GROWTH_THRESHOLD;
            
        boolean hasMetToolCallThreshold = toolCallCountSinceLastExtraction.get() >= TOOL_CALL_THRESHOLD;
        boolean atNaturalPause = !hasToolCallsInLastAssistantTurn(fullConversation);

        return hasMetTokenGrowth
            && (hasMetToolCallThreshold || atNaturalPause);
    }

    private void performExtraction(List<Message> fullConversation) {
        int currentTokens = tokenEstimator.estimate(fullConversation);
        logger.info("开始提取会话记忆，当前会话 Token: {}, 工具调用: {}, 上次提取 MessageId: {}", 
            currentTokens, toolCallCountSinceLastExtraction.get(), lastExtractedMessageId);

        List<Message> extractionMessages = buildExtractionContext(fullConversation);

        try {
            String extractedMemory = llmClient.chat(extractionMessages).getMessage().getContent();

            String existingMemory = memoryManager.read();
            String finalMemory = mergeMemories(existingMemory, extractedMemory);

            memoryManager.write(finalMemory);

            toolCallCountSinceLastExtraction.set(0);
            lastExtractedTokenCount = tokenEstimator.estimate(fullConversation);

            updateLastSummarizedMessageIdIfSafe(fullConversation);
            
            logger.info("✅ 会话记忆提取成功，写入: {}", memoryManager.getMemoryFilePath());

        } catch (Exception e) {
            logger.error("❌ 会话记忆提取失败", e);
        }
    }

    private List<Message> buildExtractionContext(List<Message> fullConversation) {
        List<Message> result = new ArrayList<>();

        result.add(Message.system(SYSTEM_PROMPT));

        String existing = memoryManager.read();
        if (existing != null && !existing.isBlank()) {
            result.add(Message.user("这是当前已有的记忆内容，请基于此进行增量更新：\n\n" + existing));
        }

        int startIndex = findBoundaryIndex(fullConversation);
        logger.debug("记忆提取对话范围: [{} - {}] 条消息", startIndex, fullConversation.size());

        for (int i = startIndex; i < fullConversation.size(); i++) {
            Message msg = fullConversation.get(i);
            if (!msg.isSystem()) {
                result.add(msg);
            }
        }

        return result;
    }

    private int findBoundaryIndex(List<Message> messages) {
        if (lastExtractedMessageId == null) {
            return Math.max(0, messages.size() - 100);
        }

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (lastExtractedMessageId.equals(msg.getId())) {
                return Math.max(0, i - 5);
            }
        }

        return Math.max(0, messages.size() - 100);
    }

    private String mergeMemories(String existing, String extracted) {
        if (existing == null || existing.isBlank()) {
            return "# Session Memory\n\n" + extracted + "\n\n---\n> Auto-extracted at " + System.currentTimeMillis();
        }

        String cleanExtracted = removeSessionMemoryHeader(extracted);

        int splitPos = existing.lastIndexOf("---\n> Auto-extracted at ");
        if (splitPos > 0) {
            String baseContent = existing.substring(0, splitPos).trim();
            return baseContent + "\n\n---\n\n" + cleanExtracted + "\n\n---\n> Auto-extracted at " + System.currentTimeMillis();
        }

        return existing + "\n\n---\n\n" + cleanExtracted + "\n\n---\n> Auto-extracted at " + System.currentTimeMillis();
    }

    private String removeSessionMemoryHeader(String extracted) {
        String result = extracted;
        if (result.startsWith("# ")) {
            int firstNewline = result.indexOf("\n");
            if (firstNewline > 0) {
                result = result.substring(firstNewline).trim();
            }
        }
        while (result.startsWith("---\n> Auto-extracted")) {
            int endOfLine = result.indexOf("\n", 20);
            if (endOfLine > 0) {
                result = result.substring(endOfLine).trim();
            } else {
                break;
            }
        }
        return result;
    }

    private void updateLastSummarizedMessageIdIfSafe(List<Message> messages) {
        if (hasToolCallsInLastAssistantTurn(messages)) {
            return;
        }

        if (!messages.isEmpty()) {
            Message lastMsg = messages.get(messages.size() - 1);
            if (lastMsg.getId() != null) {
                lastExtractedMessageId = lastMsg.getId();
                compactionState.recordMemoryExtraction(lastExtractedMessageId);
            }
        }
    }

    private boolean hasToolCallsInLastAssistantTurn(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.isAssistant()) {
                return msg.getToolCalls() != null && !msg.getToolCalls().isEmpty();
            }
            if (msg.isUser()) {
                break;
            }
        }
        return false;
    }

    public boolean hasMemory() {
        return memoryManager.hasActualContent();
    }

    public SessionMemoryManager getMemoryManager() {
        return memoryManager;
    }
}
