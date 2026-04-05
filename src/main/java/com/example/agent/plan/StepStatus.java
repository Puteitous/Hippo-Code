package com.example.agent.plan;

public enum StepStatus {
    PENDING("待执行", "步骤尚未开始执行"),

    RUNNING("执行中", "步骤正在执行"),

    SUCCESS("成功", "步骤执行成功"),

    FAILED("失败", "步骤执行失败"),

    SKIPPED("跳过", "步骤被跳过"),

    TIMEOUT("超时", "步骤执行超时");

    private final String displayName;
    private final String description;

    StepStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == TIMEOUT;
    }

    public boolean isSuccess() {
        return this == SUCCESS || this == SKIPPED;
    }
}
