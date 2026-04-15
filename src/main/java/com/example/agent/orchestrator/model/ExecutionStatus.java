package com.example.agent.orchestrator.model;

public enum ExecutionStatus {

    PENDING("待执行"),
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    SKIPPED("已跳过"),
    ROLLBACKED("已回滚");

    private final String displayName;

    ExecutionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCompleted() {
        return this == SUCCESS || this == FAILED || this == SKIPPED;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isFailed() {
        return this == FAILED || this == ROLLBACKED;
    }
}
