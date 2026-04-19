package com.example.agent.context.compressor;

import com.example.agent.context.ContextWindow;
import com.example.agent.context.budget.BudgetListener;
import com.example.agent.context.budget.BudgetThreshold;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;

public class SlidingWindowTrigger implements BudgetListener {

    private final ContextWindow contextWindow;
    private final DynamicSlidingWindow compressor;
    private final TokenEstimator tokenEstimator;
    private boolean triggered;

    public SlidingWindowTrigger(ContextWindow contextWindow, TokenEstimator tokenEstimator) {
        this.contextWindow = contextWindow;
        this.tokenEstimator = tokenEstimator;
        this.compressor = new DynamicSlidingWindow(tokenEstimator);
        this.triggered = false;
    }

    @Override
    public void onThresholdReached(BudgetThreshold threshold, int currentTokens, int maxTokens) {
        if (threshold == BudgetThreshold.SLIDING_WINDOW && !triggered) {
            performCompaction(currentTokens, maxTokens);
            triggered = true;
        }
    }

    private void performCompaction(int currentTokens, int maxTokens) {
        int targetTokens = (int) (BudgetThreshold.WARNING_75.getThresholdTokens(maxTokens) * 0.9);

        List<Message> rawMessages = contextWindow.getRawMessages();

        DynamicSlidingWindow.CompactionResult result = compressor.compact(rawMessages, targetTokens);

        contextWindow.clearInjectedWarnings();
        contextWindow.replaceMessages(result.getMessages());

        injectSummary(result);
    }

    private void injectSummary(DynamicSlidingWindow.CompactionResult result) {
        String content = String.format(
            "<system-reminder>\n" +
            "动态滑动窗口压缩已执行\n" +
            "• 范围：10K-40K tokens（动态自适应）\n" +
            "• 保留 %d / %d 个对话回合\n" +
            "• 保留 %d 条含文本的推理消息\n" +
            "• 释放了 %d tokens\n" +
            "• 状态：%s\n" +
            "</system-reminder>",
            result.getTotalTurns() - result.getRemovedTurns(),
            result.getTotalTurns(),
            result.getPreservedTextBlocks(),
            result.getSavedTokens(),
            result.isWithinOptimalRange() ? "✓ 在最佳范围内" : "⚠️ 范围外，建议继续压缩"
        );
        contextWindow.injectWarning(Message.system(content));
    }

    public void reset() {
        triggered = false;
    }

    public void register() {
        contextWindow.getBudget().addListener(this);
    }

    public void unregister() {
        contextWindow.getBudget().removeListener(this);
    }

    public boolean isTriggered() {
        return triggered;
    }
}
