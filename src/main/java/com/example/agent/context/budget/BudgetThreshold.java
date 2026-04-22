package com.example.agent.context.budget;

public enum BudgetThreshold {

    WARNING_75(0.75, "⚠️ 上下文预算使用 75%，建议开始精简输出"),
    WARNING_85(0.85, "🔶 上下文预算使用 85%，建议保存关键信息，即将压缩"),
    SLIDING_WINDOW(0.90, "� 上下文预算使用 90%，执行滑动窗口截断"),
    AUTO_COMPACT(0.95, "🚨 上下文预算使用 95%，执行智能摘要压缩"),
    BLOCKING(0.975, "🛑 剩余空间不足 3000 tokens");

    private final double ratio;
    private final String message;

    BudgetThreshold(double ratio, String message) {
        this.ratio = ratio;
        this.message = message;
    }

    public double getRatio() {
        return ratio;
    }

    public String getMessage() {
        return message;
    }

    public int getThresholdTokens(int maxTokens) {
        return (int) (maxTokens * ratio);
    }

    public static BudgetThreshold fromRatio(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio) || ratio < 0) {
            return null;
        }
        double clampedRatio = Math.min(ratio, 1.0);
        BudgetThreshold[] thresholds = values();
        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (clampedRatio >= thresholds[i].ratio) {
                return thresholds[i];
            }
        }
        return null;
    }
}
