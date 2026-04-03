package com.example.agent.tools.concurrent;

public class ToolExecutionResult {
    
    private final int index;
    private final String toolCallId;
    private final String toolName;
    private final String result;
    private final boolean success;
    private final String errorMessage;
    private final long executionTimeMs;

    private ToolExecutionResult(Builder builder) {
        this.index = builder.index;
        this.toolCallId = builder.toolCallId;
        this.toolName = builder.toolName;
        this.result = builder.result;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.executionTimeMs = builder.executionTimeMs;
    }

    public int getIndex() {
        return index;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int index;
        private String toolCallId;
        private String toolName;
        private String result;
        private boolean success = true;
        private String errorMessage;
        private long executionTimeMs;

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(this);
        }
    }

    @Override
    public String toString() {
        return "ToolExecutionResult{" +
                "index=" + index +
                ", toolCallId='" + toolCallId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", success=" + success +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}
