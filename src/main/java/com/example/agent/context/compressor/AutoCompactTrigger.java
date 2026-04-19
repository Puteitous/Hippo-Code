package com.example.agent.context.compressor;

import com.example.agent.context.ContextWindow;
import com.example.agent.context.SessionCompactionState;
import com.example.agent.context.budget.BudgetListener;
import com.example.agent.context.budget.BudgetThreshold;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;

public class AutoCompactTrigger implements BudgetListener {

    private final ContextWindow contextWindow;
    private final DynamicSlidingWindow slidingWindow;
    private final AutoCompact autoCompact;
    private final TokenEstimator tokenEstimator;
    private final SessionCompactionState state;
    private boolean compactionPerformed;

    public AutoCompactTrigger(ContextWindow contextWindow, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this.contextWindow = contextWindow;
        this.tokenEstimator = tokenEstimator;
        this.slidingWindow = new DynamicSlidingWindow(tokenEstimator);
        this.autoCompact = new AutoCompact(tokenEstimator, llmClient);
        this.state = new SessionCompactionState();
        this.compactionPerformed = false;
    }

    @Override
    public void onThresholdReached(BudgetThreshold threshold, int currentTokens, int maxTokens) {
        if (threshold == BudgetThreshold.AUTO_COMPACT && !compactionPerformed) {
            performSmartCompaction(currentTokens, maxTokens);
            compactionPerformed = true;
        }
    }

    private void performSmartCompaction(int currentTokens, int maxTokens) {
        int targetTokens = (int) (BudgetThreshold.WARNING_75.getThresholdTokens(maxTokens) * 0.9);

        List<Message> currentMessages = contextWindow.getRawMessages();

        if (state.canIncrementalCompact()) {
            DynamicSlidingWindow.CompactionResult incremental = tryIncrementalCompact(
                currentMessages, targetTokens, maxTokens
            );
            if (incremental != null) {
                applyResult(incremental, true);
                return;
            }
        }

        DynamicSlidingWindow.CompactionResult windowResult = trySlidingWindowFirst(
            currentMessages, targetTokens, maxTokens
        );

        if (windowResult != null) {
            applyResult(windowResult, false);
            return;
        }

        List<Message> compacted = autoCompact.compact(currentMessages, targetTokens);
        contextWindow.clearInjectedWarnings();
        contextWindow.replaceMessages(compacted);
        state.recordCompaction("llm_summary_" + System.currentTimeMillis());

        AutoCompact.CompactionResult llmResult = autoCompact.getLastResult();
        injectLLMCompaction(llmResult, currentTokens, maxTokens);
    }

    private DynamicSlidingWindow.CompactionResult tryIncrementalCompact(
            List<Message> messages, int targetTokens, int maxTokens) {
        String boundary = state.getLastCompactedBoundaryId();

        if (boundary == null) {
            return null;
        }

        return trySlidingWindowFirst(messages, targetTokens, maxTokens);
    }

    private DynamicSlidingWindow.CompactionResult trySlidingWindowFirst(
            List<Message> messages, int targetTokens, int maxTokens) {

        if (!shouldTrySlidingWindowFirst(messages)) {
            return null;
        }

        DynamicSlidingWindow.CompactionResult result = slidingWindow.compact(messages, targetTokens);

        int tokensAfter = tokenEstimator.estimateConversationTokens(result.getMessages());

        if (tokensAfter < BudgetThreshold.SLIDING_WINDOW.getThresholdTokens(maxTokens)
            && result.isWithinOptimalRange()) {
            return result;
        }

        return null;
    }

    private void applyResult(DynamicSlidingWindow.CompactionResult result, boolean incremental) {
        contextWindow.clearInjectedWarnings();
        contextWindow.replaceMessages(result.getMessages());
        state.recordCompaction("turn_" + (result.getTotalTurns() - result.getRemovedTurns()));
        injectSlidingWindowSuccess(result, incremental);
    }

    private boolean shouldTrySlidingWindowFirst(List<Message> messages) {
        long toolMessageCount = messages.stream().filter(Message::isTool).count();
        
        return toolMessageCount > 5
            && messages.size() > 20;
    }

    private void injectSlidingWindowSuccess(DynamicSlidingWindow.CompactionResult result, boolean incremental) {
        String content = String.format(
            "<system-reminder>\n" +
            "✅ %s零成本压缩完成（动态滑动窗口）\n" +
            "• 算法：动态 token 范围（10K-40K），无 LLM 调用\n" +
            "• 保留 %d / %d 个完整对话回合\n" +
            "• 保留 %d 条含文本的推理消息\n" +
            "• 释放 %d tokens\n" +
            "• 状态：本次压缩 #%d\n" +
            "</system-reminder>",
            incremental ? "增量" : "",
            result.getTotalTurns() - result.getRemovedTurns(),
            result.getTotalTurns(),
            result.getPreservedTextBlocks(),
            result.getSavedTokens(),
            state.getCompactionCount()
        );
        contextWindow.injectWarning(Message.system(content));
    }

    private void injectLLMCompaction(AutoCompact.CompactionResult result, int beforeTokens, int maxTokens) {
        int savedTokens = beforeTokens - result.getTokenCountAfter();
        String content = String.format(
            "<system-reminder>\n" +
            "🔄 智能摘要压缩完成（第 %d 次压缩）\n" +
            "• 算法：LLM 结构化摘要\n" +
            "• 融合 %d 条早期历史为摘要\n" +
            "• 释放 %d tokens (节省 %.1f%%)\n" +
            "• 注：动态滑动窗口无效，已降级为深度压缩\n" +
            "</system-reminder>",
            state.getCompactionCount(),
            result.getMergedCount(),
            savedTokens,
            (double) savedTokens / beforeTokens * 100
        );
        contextWindow.injectWarning(Message.system(content));
    }

    public void reset() {
        compactionPerformed = false;
    }

    public void fullReset() {
        reset();
        state.reset();
    }

    public void register() {
        contextWindow.getBudget().addListener(this);
    }

    public void unregister() {
        contextWindow.getBudget().removeListener(this);
    }

    public boolean isCompactionPerformed() {
        return compactionPerformed;
    }

    public SessionCompactionState getState() {
        return state;
    }
}
