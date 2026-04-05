package com.example.agent.plan;

import java.util.HashMap;
import java.util.Map;

public class StepResult {

    private final String stepId;
    private final StepStatus status;
    private final String output;
    private final String error;
    private final long durationMs;
    private final Map<String, Object> metadata;

    private StepResult(Builder builder) {
        this.stepId = builder.stepId;
        this.status = builder.status;
        this.output = builder.output;
        this.error = builder.error;
        this.durationMs = builder.durationMs;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
    }

    public String getStepId() {
        return stepId;
    }

    public StepStatus getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StepResult success(String stepId, String output) {
        return builder()
                .stepId(stepId)
                .status(StepStatus.SUCCESS)
                .output(output)
                .build();
    }

    public static StepResult failure(String stepId, String error) {
        return builder()
                .stepId(stepId)
                .status(StepStatus.FAILED)
                .error(error)
                .build();
    }

    public static StepResult skipped(String stepId, String reason) {
        return builder()
                .stepId(stepId)
                .status(StepStatus.SKIPPED)
                .output(reason)
                .build();
    }

    @Override
    public String toString() {
        return "StepResult{" +
                "stepId='" + stepId + '\'' +
                ", status=" + status +
                ", durationMs=" + durationMs +
                '}';
    }

    public static class Builder {
        private String stepId;
        private StepStatus status = StepStatus.PENDING;
        private String output;
        private String error;
        private long durationMs = 0;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder status(StepStatus status) {
            this.status = status;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public StepResult build() {
            if (stepId == null || stepId.isEmpty()) {
                throw new IllegalArgumentException("Step ID is required");
            }
            return new StepResult(this);
        }
    }
}
