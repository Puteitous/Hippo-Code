package com.example.agent.memory;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BackgroundExtractor {

    private static final int TOKEN_THRESHOLD = 5000;
    private static final int TOOL_CALL_THRESHOLD = 3;

    private final SessionMemoryManager memoryManager;
    private final SessionCompactionState compactionState;
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;

    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final AtomicBoolean extractionInProgress = new AtomicBoolean(false);
    private String lastExtractedMessageId;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String EXTRACTION_PROMPT = """
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
        - 相同/相似的内容合并
        - 输出只包含 3 个章节，不要其他解释

        对话历史：
        ```
        %s
        ```

        结构化记忆：
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
            toolCallCount.incrementAndGet();
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

        boolean tokenTrigger = currentTokens >= TOKEN_THRESHOLD;
        boolean toolTrigger = toolCallCount.get() >= TOOL_CALL_THRESHOLD;

        return tokenTrigger || toolTrigger;
    }

    private void performExtraction(List<Message> fullConversation) {
        String conversationText = formatConversation(fullConversation);

        String prompt = String.format(EXTRACTION_PROMPT, truncate(conversationText, 15000));

        try {
            String extractedMemory = llmClient.generateSync(prompt);

            String existingMemory = memoryManager.read();
            String finalMemory = mergeMemories(existingMemory, extractedMemory);

            memoryManager.write(finalMemory);

            toolCallCount.set(0);

            updateLastSummarizedMessageIdIfSafe(fullConversation);

        } catch (Exception e) {
        }
    }

    private String formatConversation(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, messages.size() - 100); i < messages.size(); i++) {
            Message msg = messages.get(i);
            String role = msg.getRole();
            String content = truncate(msg.getContent(), 500);
            sb.append("[").append(role).append("] ").append(content).append("\n\n");
        }
        return sb.toString();
    }

    private String mergeMemories(String existing, String extracted) {
        if (existing == null || existing.isBlank()) {
            return "# Session Memory\n\n" + extracted + "\n\n---\n> Auto-extracted at " + System.currentTimeMillis();
        }

        return existing + "\n\n---\n\n" + extracted;
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

    private String truncate(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "\n... [truncated]";
    }

    public boolean hasMemory() {
        return memoryManager.exists();
    }

    public String getMemory() {
        return memoryManager.read();
    }

    public int getToolCallCount() {
        return toolCallCount.get();
    }

    public boolean isExtractionInProgress() {
        return extractionInProgress.get();
    }

    public String getLastExtractedMessageId() {
        return lastExtractedMessageId;
    }

    public SessionMemoryManager getMemoryManager() {
        return memoryManager;
    }

    public boolean waitForExtractionCompletion(long timeoutMillis) {
        long start = System.currentTimeMillis();
        while (extractionInProgress.get()) {
            if (System.currentTimeMillis() - start > timeoutMillis) {
                return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
