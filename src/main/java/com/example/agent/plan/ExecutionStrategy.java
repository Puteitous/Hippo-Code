package com.example.agent.plan;

public enum ExecutionStrategy {
    
    SEQUENTIAL("顺序执行", "按顺序依次执行每个步骤"),

    PARALLEL("并行执行", "尽可能并行执行独立步骤"),

    CONDITIONAL("条件执行", "根据条件决定执行路径"),

    ADAPTIVE("自适应执行", "根据运行时情况动态调整执行策略");

    private final String displayName;
    private final String description;

    ExecutionStrategy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
