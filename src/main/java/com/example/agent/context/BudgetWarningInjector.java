package com.example.agent.context;

import com.example.agent.context.budget.BudgetListener;
import com.example.agent.context.budget.BudgetThreshold;
import com.example.agent.llm.model.Message;

public class BudgetWarningInjector implements BudgetListener {

    private final ContextWindow contextWindow;
    private int lastNudgeTokenMilestone = 0;
    private boolean autoCompactAnnounced = false;
    private static final int NUDGE_INTERVAL = 10000;
    private static final double AUTO_COMPACT_ANNOUNCE_THRESHOLD = 0.25;

    public BudgetWarningInjector(ContextWindow contextWindow) {
        this.contextWindow = contextWindow;
    }

    @Override
    public void onBudgetUpdated(int currentTokens, int maxTokens, double usageRatio) {
        if (!autoCompactAnnounced && usageRatio >= AUTO_COMPACT_ANNOUNCE_THRESHOLD) {
            injectAutoCompactEnabled();
            autoCompactAnnounced = true;
        }
        
        checkEfficiencyNudge(currentTokens);
    }

    @Override
    public void onThresholdReached(BudgetThreshold threshold, int currentTokens, int maxTokens) {
        switch (threshold) {
            case WARNING_75:
                injectEfficiencyHint();
                break;
            case WARNING_85:
                injectToolOptimizationHint();
                break;
            case SLIDING_WINDOW:
            case AUTO_COMPACT:
                injectCompactionPerformed();
                break;
            case BLOCKING:
                break;
        }
    }

    private void injectAutoCompactEnabled() {
        String content = 
            "<system-reminder>\n" +
            "自动压缩已启用。当上下文窗口接近满载时，早期消息将被自动摘要，\n" +
            "你可以无缝继续工作。不需要停止或仓促 — 通过自动压缩，你拥有无限上下文。\n" +
            "</system-reminder>";
        contextWindow.injectWarning(Message.system(content));
    }

    private boolean hasInjectedMessage(String marker) {
        return contextWindow.getEffectiveMessages().stream()
            .filter(Message::isSystem)
            .anyMatch(m -> m.getContent() != null && m.getContent().contains(marker));
    }

    private void injectEfficiencyHint() {
        String content = 
            "<system-reminder>\n" +
            "上下文效率提示：为获得最佳性能，建议：\n" +
            "• 只从工具请求必要的数据\n" +
            "• 输出聚焦核心推理而非重复\n" +
            "• 自动压缩机制会自动处理历史管理\n" +
            "</system-reminder>";
        contextWindow.injectWarning(Message.system(content));
    }

    private void injectToolOptimizationHint() {
        String content = 
            "<system-reminder>\n" +
            "上下文效率提醒：请优先调用返回聚焦、精简结果的工具。\n" +
            "除非必要，避免大范围搜索或读取大文件。\n" +
            "需要时自动压缩会自动激活。\n" +
            "</system-reminder>";
        contextWindow.injectWarning(Message.system(content));
    }

    private void injectCompactionPerformed() {
        String content = 
            "<system-reminder>\n" +
            "上下文已自动压缩：早期对话历史已被摘要。\n" +
            "请继续正常工作 — 关键上下文已保留。\n" +
            "</system-reminder>";
        contextWindow.injectWarning(Message.system(content));
    }

    private void checkEfficiencyNudge(int currentTokens) {
        if (currentTokens < 0) {
            return;
        }
        int milestone = currentTokens / NUDGE_INTERVAL;
        if (milestone > lastNudgeTokenMilestone && milestone > 0) {
            lastNudgeTokenMilestone = milestone;
        }
    }

    public void reset() {
        lastNudgeTokenMilestone = 0;
        autoCompactAnnounced = false;
    }

    public void register() {
        contextWindow.getBudget().addListener(this);
    }

    public void unregister() {
        contextWindow.getBudget().removeListener(this);
    }

}
