package com.example.agent.context.compressor;

import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.SessionMemoryManager;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContextSummarizer {
    private static final Logger logger = LoggerFactory.getLogger(ContextSummarizer.class);

    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;
    private final SessionMemoryManager memoryManager;
    private final CompactForkExecutor forkExecutor;
    private final String sessionId;
    private CompactionResult lastResult;
    private String customInstruction;

    public ContextSummarizer(TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(tokenEstimator, llmClient, null, null);
    }

    public ContextSummarizer(TokenEstimator tokenEstimator, LlmClient llmClient, String sessionId) {
        this(tokenEstimator, llmClient, new SessionMemoryManager(sessionId), sessionId);
    }

    public ContextSummarizer(TokenEstimator tokenEstimator, LlmClient llmClient, SessionMemoryManager memoryManager) {
        this(tokenEstimator, llmClient, memoryManager, null);
    }

    private ContextSummarizer(TokenEstimator tokenEstimator, LlmClient llmClient, 
                             SessionMemoryManager memoryManager, String sessionId) {
        this.tokenEstimator = tokenEstimator;
        this.llmClient = llmClient;
        this.memoryManager = memoryManager;
        this.sessionId = sessionId;
        this.forkExecutor = new CompactForkExecutor();
    }

    public List<Message> compact(List<Message> messages, int targetTokens) {
        List<Message> immutable = List.copyOf(messages);
        int initialTokens = tokenEstimator.estimate(immutable);

        Message systemMessage = null;
        List<Message> historyMessages = new ArrayList<>();

        for (Message msg : immutable) {
            if (msg.isSystem() && systemMessage == null) {
                systemMessage = msg;
            } else {
                historyMessages.add(msg);
            }
        }

        int splitIndex = Math.max(1, historyMessages.size() / 2);
        List<Message> toSummarize = historyMessages.subList(0, splitIndex);
        List<Message> toKeep = historyMessages.subList(splitIndex, historyMessages.size());

        String summary = getOrGenerateSummary(toSummarize);

        List<Message> result = new ArrayList<>();
        if (systemMessage != null) {
            result.add(systemMessage);
        }

        result.add(0, Message.system("--- SESSION COMPACTION BOUNDARY ---"));
        result.add(Message.user(createSummaryHeader(summary, toSummarize.size(), initialTokens)));
        result.addAll(toKeep);

        int finalTokens = tokenEstimator.estimate(result);

        lastResult = new CompactionResult(
            toSummarize.size(),
            initialTokens,
            finalTokens,
            summary
        );

        return result;
    }

    private String getOrGenerateSummary(List<Message> messages) {
        if (memoryManager != null) {
            String existingMemory = memoryManager.read();
            if (existingMemory != null && !existingMemory.isBlank()) {
                return existingMemory + "\n\n> ✅ 从 session-memory.md 加载，无需重新生成";
            }
        }
        return generateSummary(messages);
    }

    private String generateSummary(List<Message> messages) {
        String conversationText = messages.stream()
            .map(msg -> String.format("%s: %s", msg.getRole(), truncate(msg.getContent(), 500)))
            .collect(Collectors.joining("\n"));

        String baseInstruction = (customInstruction != null && !customInstruction.trim().isEmpty())
            ? customInstruction
            : "请将以下对话历史压缩成精准摘要，保留：\n" +
              "1. 用户核心需求和目标\n" +
              "2. 关键决策和结论\n" +
              "3. 已完成的重要操作\n" +
              "4. 需要记住的技术上下文\n\n" +
              "**不要**保留：工具调用的详细输出、中间调试信息、重复内容。";

        String compactionPrompt = String.format(
            "## 结构化对话摘要任务\n\n" +
            "%s\n" +
            "输出格式：使用 Markdown 列表，简洁专业。\n\n" +
            "基于完整对话上下文进行准确摘要。",
            baseInstruction
        );

        try {
            if (sessionId != null) {
                logger.info("🚀 使用 Fork Agent 执行 LLM 压缩 (Prompt Cache 优化模式");
                CompactForkExecutor.CompactResult result = forkExecutor.executeForkedCompaction(sessionId, compactionPrompt);
                
                if (result.isSuccess()) {
                    logger.info("✅ Fork Agent 压缩成功 (Cache 模式: {})", 
                        result.isUsedFork() ? "Fork+缓存共享" : "直接执行");
                    return result.getSummary();
                } else {
                    logger.warn("⚠️ Fork 压缩失败，回退到直接执行: {}", result.getError());
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️ Fork 压缩异常，回退到直接执行: {}", e.getMessage());
        }

        return fallbackSummary(messages);
    }
    
    private String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private String fallbackSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return "## 历史摘要\n- 对话历史为空";
        }
        Message lastMsg = messages.get(messages.size() - 1);
        String contentPreview = "";
        if (lastMsg.getContent() != null && !lastMsg.getContent().isEmpty()) {
            int previewLen = Math.min(100, lastMsg.getContent().length());
            contentPreview = lastMsg.getContent().substring(0, previewLen) + "...";
        }
        return String.format(
            "## 历史摘要（共 %d 条消息）\n" +
            "- 早期对话已被压缩\n" +
            "- 最近对话完整保留\n" +
            "- 关键上下文：%s",
            messages.size(),
            contentPreview
        );
    }

    private String createSummaryHeader(String summary, int messagesMerged, int beforeTokens) {
        return String.format(
            "## [AutoCompact] 历史摘要\n" +
            "> 融合了前 %d 条消息的核心内容 | 原始 %d tokens\n\n" +
            "%s\n\n" +
            "---",
            messagesMerged,
            beforeTokens,
            summary
        );
    }

    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "\n... [内容已截断]";
    }

    public CompactionResult getLastResult() {
        return lastResult;
    }

    public void setCustomInstruction(String customInstruction) {
        this.customInstruction = customInstruction;
    }

    public static class CompactionResult {
        private final int mergedCount;
        private final int tokenCountBefore;
        private final int tokenCountAfter;
        private final String summary;

        public CompactionResult(int mergedCount, int tokenCountBefore, int tokenCountAfter, String summary) {
            this.mergedCount = mergedCount;
            this.tokenCountBefore = tokenCountBefore;
            this.tokenCountAfter = tokenCountAfter;
            this.summary = summary;
        }

        public int getMergedCount() { return mergedCount; }
        public int getTokenCountBefore() { return tokenCountBefore; }
        public int getTokenCountAfter() { return tokenCountAfter; }
        public String getSummary() { return summary; }
    }
}
