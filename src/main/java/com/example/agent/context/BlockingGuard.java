package com.example.agent.context;

import com.example.agent.context.budget.BudgetListener;
import com.example.agent.context.budget.BudgetThreshold;

public class BlockingGuard implements BudgetListener {

    private final ContextWindow contextWindow;
    private boolean blocked;

    public BlockingGuard(ContextWindow contextWindow) {
        this.contextWindow = contextWindow;
        this.blocked = false;
    }

    @Override
    public void onThresholdReached(BudgetThreshold threshold, int currentTokens, int maxTokens) {
        if (threshold == BudgetThreshold.BLOCKING) {
            blocked = true;
        }
    }

    public boolean isBlocked() {
        return blocked;
    }

    public int getRemainingTokens(int maxTokens) {
        return Math.max(0, maxTokens - contextWindow.getBudget().getCurrentTokens());
    }

    public boolean canAddMessage() {
        return !blocked;
    }

    public boolean canCallTool() {
        return !blocked;
    }

    public String getStatusMessage() {
        return getUserBlockingMessage();
    }

    public String getUserBlockingMessage() {
        int remaining = getRemainingTokens(contextWindow.getBudget().getMaxTokens());
        return String.format(
            "⚠️ 上下文空间不足\n" +
            "剩余约 %,d tokens。为保证系统稳定，已暂停新消息。\n" +
            "请执行 /compact 压缩历史后继续。",
            remaining
        );
    }

    public void reset() {
        blocked = false;
    }

    public void register() {
        contextWindow.getBudget().addListener(this);
    }

    public void unregister() {
        contextWindow.getBudget().removeListener(this);
    }
}
