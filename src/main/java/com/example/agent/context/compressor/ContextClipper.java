package com.example.agent.context.compressor;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.SessionMemoryManager;
import com.example.agent.service.TokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContextClipper {

    public static final int MIN_TOKENS_TARGET = 10000;
    public static final int MAX_TOKENS_TARGET = 40000;
    public static final int MIN_TEXT_BLOCK_MESSAGES = 5;

    private final TokenEstimator tokenEstimator;
    private final SessionMemoryManager memoryManager;
    private final LlmClient llmClient;
    private final int minTokens;
    private final int maxTokens;
    private final int minTextBlockMessages;

    public ContextClipper(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null, null, MIN_TOKENS_TARGET, MAX_TOKENS_TARGET, MIN_TEXT_BLOCK_MESSAGES);
    }

    public ContextClipper(TokenEstimator tokenEstimator, String sessionId, LlmClient llmClient) {
        this(tokenEstimator, new SessionMemoryManager(sessionId), llmClient, MIN_TOKENS_TARGET, MAX_TOKENS_TARGET, MIN_TEXT_BLOCK_MESSAGES);
    }

    public ContextClipper(TokenEstimator tokenEstimator, SessionMemoryManager memoryManager, LlmClient llmClient) {
        this(tokenEstimator, memoryManager, llmClient, MIN_TOKENS_TARGET, MAX_TOKENS_TARGET, MIN_TEXT_BLOCK_MESSAGES);
    }

    public ContextClipper(TokenEstimator tokenEstimator, SessionMemoryManager memoryManager, LlmClient llmClient, int minTokens, int maxTokens, int minTextBlockMessages) {
        this.tokenEstimator = tokenEstimator;
        this.memoryManager = memoryManager;
        this.llmClient = llmClient;
        this.minTokens = minTokens;
        this.maxTokens = maxTokens;
        this.minTextBlockMessages = minTextBlockMessages;
    }

    public CompactionResult compact(List<Message> messages, int targetTokens, SessionCompactionState state) {
        if (messages == null || messages.size() <= 2) {
            return new CompactionResult(new ArrayList<>(messages), 0, 0, 0, false, 0, false, false);
        }

        Message systemMessage = messages.get(0).isSystem() ? messages.get(0) : null;
        List<Message> conversationMessages = systemMessage != null 
            ? new ArrayList<>(messages.subList(1, messages.size()))
            : new ArrayList<>(messages);

        List<ConversationTurn> turns = groupIntoCompleteTurns(conversationMessages);
        int originalTokenCount = tokenEstimator.estimateConversationTokens(messages);

        BoundaryResult boundary = findSummaryBoundaryWithValidation(messages, state);
        SelectionResult selected;

        if ("resumed_session".equals(boundary.reason)) {
            selected = expandWindowFromTailToTarget(turns, targetTokens);
        } else {
            int anchorTurnIndex = findTurnIndexForMessage(turns, boundary.startIndex);
            List<ConversationTurn> turnsAfterAnchor = turns.subList(anchorTurnIndex, turns.size());
            selected = buildSlidingWindowFromAnchor(turnsAfterAnchor, targetTokens);
        }

        List<Message> truncatedMessages = reassemble(null, selected.turns);

        int deletedTurnCount = turns.size() - selected.turns.size();
        boolean usedSessionMemory = false;
        boolean usedLlmSummary = false;

        if (deletedTurnCount > 0) {
            Message summaryHeader = createSummaryHeader(turns.subList(0, deletedTurnCount));
            if (summaryHeader != null) {
                truncatedMessages.add(0, summaryHeader);
                usedSessionMemory = memoryManager != null && memoryManager.exists();
                usedLlmSummary = !usedSessionMemory && llmClient != null;
            }
        }

        List<Message> result = new ArrayList<>();
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        result.addAll(truncatedMessages);

        int finalTokenCount = tokenEstimator.estimateConversationTokens(result);
        
        return new CompactionResult(
            result,
            deletedTurnCount,
            turns.size(),
            originalTokenCount - finalTokenCount,
            selected.withinRange,
            selected.textBlockCount,
            usedSessionMemory,
            usedLlmSummary
        );
    }

    private int findTurnIndexForMessage(List<ConversationTurn> turns, int messageIndex) {
        int currentMsgIdx = 0;
        for (int i = 0; i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            int turnSize = turn.getMessages().size();
            if (currentMsgIdx + turnSize > messageIndex) {
                return i;
            }
            currentMsgIdx += turnSize;
        }
        return Math.max(0, turns.size() - 1);
    }

    private Message createSummaryHeader(List<ConversationTurn> deletedTurns) {
        String summaryContent;
        String source;

        if (memoryManager != null && memoryManager.exists()) {
            summaryContent = memoryManager.read();
            source = "✅ session-memory.md";
        } else if (llmClient != null && !deletedTurns.isEmpty()) {
            summaryContent = generateLlmSummary(deletedTurns);
            source = "🔄 LLM 实时生成";
        } else {
            summaryContent = createFallbackSummary(deletedTurns);
            source = "📝 自动摘要";
        }

        String content = String.format(
            "## [Sliding Window] 早期会话摘要\n\n" +
            "> 来源：%s | 已合并 %d 轮早期对话\n\n" +
            "%s\n\n" +
            "---\n\n" +
            "> 以上为早期会话摘要，最近对话完整保留",
            source,
            deletedTurns.size(),
            summaryContent
        );

        return Message.system(content);
    }

    private String generateLlmSummary(List<ConversationTurn> deletedTurns) {
        String conversationText = deletedTurns.stream()
            .flatMap(turn -> turn.getMessages().stream())
            .map(msg -> String.format("%s: %s", msg.getRole(), truncate(msg.getContent(), 500)))
            .collect(Collectors.joining("\n"));

        String prompt = String.format(
            "## 结构化摘要任务\n\n" +
            "将以下删除的早期对话压缩成精准摘要，按 3 个维度组织：\n\n" +
            "### 1. 关键决策\n" +
            "### 2. 错误与修复\n" +
            "### 3. 已完成进度\n\n" +
            "对话内容：\n```\n%s\n```\n\n" +
            "摘要：",
            truncate(conversationText, 12000)
        );

        try {
            return llmClient.generateSync(prompt);
        } catch (Exception e) {
            return createFallbackSummary(deletedTurns);
        }
    }

    private String createFallbackSummary(List<ConversationTurn> deletedTurns) {
        return String.format(
            "### 早期会话摘要\n\n" +
            "- 已合并 %d 轮早期对话\n" +
            "- 最近对话完整保留",
            deletedTurns.size()
        );
    }

    public static class BoundaryResult {
        public final int startIndex;
        public final boolean isValid;
        public final String reason;

        public BoundaryResult(int startIndex, boolean isValid, String reason) {
            this.startIndex = startIndex;
            this.isValid = isValid;
            this.reason = reason;
        }
    }

    public BoundaryResult findSummaryBoundaryWithValidation(
            List<Message> messages, SessionCompactionState state) {
        if (hasToolCallsInLastAssistantTurn(messages)) {
            return new BoundaryResult(
                Math.max(0, messages.size() - 5), 
                true, 
                "tool_call_in_progress"
            );
        }

        if (state != null && state.hasValidSummaryBoundary()) {
            String boundaryId = state.getLastSummarizedMessageId();
            for (int i = 0; i < messages.size(); i++) {
                if (boundaryId.equals(messages.get(i).getId())) {
                    return new BoundaryResult(i + 1, true, "found");
                }
            }
            return new BoundaryResult(-1, false, "resumed_session");
        }

        return new BoundaryResult(0, true, "no_boundary");
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

    private SelectionResult buildSlidingWindowFromAnchor(List<ConversationTurn> turnsAfterAnchor, int targetMax) {
        List<ConversationTurn> selected = new ArrayList<>();
        int runningTokens = 0;
        int textBlockCount = 0;
        boolean withinRange = false;

        int effectiveMin = minTokens;
        int effectiveMax = Math.min(maxTokens, targetMax);

        int startIdx = adjustStartIndexToPreserveInvariants(turnsAfterAnchor, 0);

        for (int i = startIdx; i < turnsAfterAnchor.size(); i++) {
            ConversationTurn turn = turnsAfterAnchor.get(i);
            int turnTokens = turn.getTokenCount();

            if (i >= turnsAfterAnchor.size() - 3) {
                selected.add(turn);
                runningTokens += turnTokens;
                if (turn.hasSignificantText()) {
                    textBlockCount++;
                }
                continue;
            }

            if (textBlockCount < minTextBlockMessages) {
                selected.add(turn);
                runningTokens += turnTokens;
                if (turn.hasSignificantText()) {
                    textBlockCount++;
                }
                continue;
            }

            if (runningTokens + turnTokens > effectiveMax && runningTokens >= effectiveMin) {
                withinRange = true;
                break;
            }

            selected.add(turn);
            runningTokens += turnTokens;
            if (turn.hasSignificantText()) {
                textBlockCount++;
            }

            if (runningTokens >= effectiveMax) {
                withinRange = runningTokens >= effectiveMin;
                break;
            }
        }

        return new SelectionResult(selected, withinRange, textBlockCount);
    }

    private SelectionResult expandWindowFromTailToTarget(List<ConversationTurn> turns, int targetMax) {
        int effectiveMin = minTokens;
        int effectiveMax = Math.min(maxTokens, targetMax);
        int startIdx = expandWindowFromTail(turns, effectiveMin, effectiveMax);

        List<ConversationTurn> selected = turns.subList(startIdx, turns.size());
        int runningTokens = selected.stream().mapToInt(ConversationTurn::getTokenCount).sum();
        int textBlockCount = (int) selected.stream().filter(ConversationTurn::hasSignificantText).count();
        boolean withinRange = runningTokens >= effectiveMin && runningTokens <= effectiveMax;

        return new SelectionResult(selected, withinRange, textBlockCount);
    }

    private int expandWindowFromTail(List<ConversationTurn> turns, int minTokens, int maxTokens) {
        if (turns.size() <= 3) {
            return 0;
        }

        int runningTokens = 0;
        int textBlockCount = 0;
        int startIdx = turns.size();

        while (startIdx > 0) {
            startIdx--;
            ConversationTurn turn = turns.get(startIdx);
            runningTokens += turn.getTokenCount();
            if (turn.hasSignificantText()) {
                textBlockCount++;
            }

            if (runningTokens >= minTokens && textBlockCount >= minTextBlockMessages) {
                break;
            }

            if (runningTokens >= maxTokens) {
                break;
            }
        }

        return adjustStartIndexToPreserveInvariants(turns, startIdx);
    }

    private int adjustStartIndexToPreserveInvariants(List<ConversationTurn> turns, int proposedStart) {
        if (turns.isEmpty()) {
            return 0;
        }

        int safeStart = proposedStart;

        while (safeStart < turns.size() && turns.get(safeStart).hasToolPair()) {
            safeStart++;
        }

        while (safeStart > 0) {
            safeStart--;
            if (!turns.get(safeStart).hasToolPair()) {
                break;
            }
        }

        return Math.max(0, safeStart);
    }

    private List<ConversationTurn> groupIntoCompleteTurns(List<Message> messages) {
        List<ConversationTurn> turns = new ArrayList<>();
        ConversationTurn currentTurn = new ConversationTurn();

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);

            if (msg.isUser() && currentTurn.hasContent()) {
                turns.add(currentTurn);
                currentTurn = new ConversationTurn();
            }

            currentTurn.add(msg);
        }

        if (currentTurn.hasContent()) {
            turns.add(currentTurn);
        }

        return turns;
    }

    private List<Message> reassemble(Message systemMessage, List<ConversationTurn> turns) {
        List<Message> result = new ArrayList<>();
        
        if (systemMessage != null) {
            result.add(systemMessage);
        }

        for (ConversationTurn turn : turns) {
            result.addAll(turn.getMessages());
        }

        return result;
    }

    public CompactionResult compact(List<Message> messages, int targetTokens) {
        return compact(messages, targetTokens, null);
    }

    private String truncate(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "\n... [truncated]";
    }

    private class ConversationTurn {
        private final List<Message> messages = new ArrayList<>();
        private int tokenCount = 0;
        private boolean hasToolPair = false;
        private boolean hasSignificantText = false;

        void add(Message msg) {
            messages.add(msg);
            int tokens = tokenEstimator.estimateMessageTokens(msg);
            tokenCount += tokens;
            
            if (msg.isTool() || (msg.isAssistant() && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty())) {
                hasToolPair = true;
            }

            String content = msg.getContent();
            if (content != null && content.length() > 50 && !msg.isTool()) {
                hasSignificantText = true;
            }
        }

        boolean hasContent() {
            return !messages.isEmpty();
        }

        List<Message> getMessages() {
            return messages;
        }

        int getTokenCount() {
            return tokenCount;
        }

        boolean hasToolPair() {
            return hasToolPair;
        }

        boolean hasSignificantText() {
            return hasSignificantText;
        }
    }

    private static class SelectionResult {
        final List<ConversationTurn> turns;
        final boolean withinRange;
        final int textBlockCount;

        SelectionResult(List<ConversationTurn> turns, boolean withinRange, int textBlockCount) {
            this.turns = turns;
            this.withinRange = withinRange;
            this.textBlockCount = textBlockCount;
        }
    }

    public static class CompactionResult {
        private final List<Message> compactedMessages;
        private final int removedTurns;
        private final int totalTurns;
        private final int tokensSaved;
        private final boolean withinOptimalRange;
        private final int significantTextBlocks;
        private final boolean usedSessionMemory;
        private final boolean usedLlmSummary;

        public CompactionResult(List<Message> compactedMessages, int removedTurns, int totalTurns, int tokensSaved,
                               boolean withinOptimalRange, int significantTextBlocks,
                               boolean usedSessionMemory, boolean usedLlmSummary) {
            this.compactedMessages = compactedMessages;
            this.removedTurns = removedTurns;
            this.totalTurns = totalTurns;
            this.tokensSaved = tokensSaved;
            this.withinOptimalRange = withinOptimalRange;
            this.significantTextBlocks = significantTextBlocks;
            this.usedSessionMemory = usedSessionMemory;
            this.usedLlmSummary = usedLlmSummary;
        }

        public List<Message> getCompactedMessages() { return compactedMessages; }
        public int getRemovedTurns() { return removedTurns; }
        public int getTotalTurns() { return totalTurns; }
        public int getTokensSaved() { return tokensSaved; }
        public boolean isWithinOptimalRange() { return withinOptimalRange; }
        public int getSignificantTextBlocks() { return significantTextBlocks; }
        public boolean isUsedSessionMemory() { return usedSessionMemory; }
        public boolean isUsedLlmSummary() { return usedLlmSummary; }

        public List<Message> getMessages() { return compactedMessages; }
        public int getPreservedTextBlocks() { return significantTextBlocks; }
        public int getSavedTokens() { return tokensSaved; }
    }
}
