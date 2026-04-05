package com.example.agent.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlanResult {

    private final String planId;
    private final boolean success;
    private final Map<String, StepResult> stepResults;
    private final String summary;
    private final long totalDurationMs;
    private final Map<String, Object> outputs;

    private PlanResult(Builder builder) {
        this.planId = builder.planId;
        this.success = builder.success;
        this.stepResults = builder.stepResults != null ? new LinkedHashMap<>(builder.stepResults) : new LinkedHashMap<>();
        this.summary = builder.summary;
        this.totalDurationMs = builder.totalDurationMs;
        this.outputs = builder.outputs != null ? new HashMap<>(builder.outputs) : new HashMap<>();
    }

    public String getPlanId() {
        return planId;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, StepResult> getStepResults() {
        return Collections.unmodifiableMap(stepResults);
    }

    public String getSummary() {
        return summary;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public Map<String, Object> getOutputs() {
        return Collections.unmodifiableMap(outputs);
    }

    public StepResult getStepResult(String stepId) {
        return stepResults.get(stepId);
    }

    public List<StepResult> getFailedSteps() {
        return stepResults.values().stream()
                .filter(r -> !r.isSuccess())
                .toList();
    }

    public List<StepResult> getSuccessfulSteps() {
        return stepResults.values().stream()
                .filter(StepResult::isSuccess)
                .toList();
    }

    public int getTotalStepCount() {
        return stepResults.size();
    }

    public int getSuccessCount() {
        return (int) stepResults.values().stream().filter(StepResult::isSuccess).count();
    }

    public int getFailureCount() {
        return (int) stepResults.values().stream().filter(r -> !r.isSuccess()).count();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PlanResult success(String planId, Map<String, StepResult> results) {
        return builder()
                .planId(planId)
                .success(true)
                .stepResults(results)
                .build();
    }

    public static PlanResult failure(String planId, String error) {
        return builder()
                .planId(planId)
                .success(false)
                .summary(error)
                .build();
    }

    @Override
    public String toString() {
        return "PlanResult{" +
                "planId='" + planId + '\'' +
                ", success=" + success +
                ", steps=" + getSuccessCount() + "/" + getTotalStepCount() +
                ", durationMs=" + totalDurationMs +
                '}';
    }

    public static class Builder {
        private String planId;
        private boolean success = true;
        private Map<String, StepResult> stepResults = new LinkedHashMap<>();
        private String summary;
        private long totalDurationMs = 0;
        private Map<String, Object> outputs = new HashMap<>();

        public Builder planId(String planId) {
            this.planId = planId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder stepResults(Map<String, StepResult> stepResults) {
            this.stepResults = stepResults;
            return this;
        }

        public Builder stepResult(String stepId, StepResult result) {
            this.stepResults.put(stepId, result);
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder totalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
            return this;
        }

        public Builder outputs(Map<String, Object> outputs) {
            this.outputs = outputs;
            return this;
        }

        public Builder output(String key, Object value) {
            this.outputs.put(key, value);
            return this;
        }

        public PlanResult build() {
            if (planId == null || planId.isEmpty()) {
                planId = "plan-" + System.currentTimeMillis();
            }
            return new PlanResult(this);
        }
    }
}
