package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionPlan {

    private final String id;
    private final IntentResult intent;
    private final List<ExecutionStep> steps;
    private final ExecutionStrategy strategy;
    private final Map<String, Object> variables;
    private final Map<String, Object> context;
    private final long createdAt;

    private ExecutionPlan(Builder builder) {
        this.id = builder.id;
        this.intent = builder.intent;
        this.steps = builder.steps != null ? new ArrayList<>(builder.steps) : new ArrayList<>();
        this.strategy = builder.strategy;
        this.variables = builder.variables != null ? new HashMap<>(builder.variables) : new HashMap<>();
        this.context = builder.context != null ? new HashMap<>(builder.context) : new HashMap<>();
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public IntentResult getIntent() {
        return intent;
    }

    public List<ExecutionStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public ExecutionStrategy getStrategy() {
        return strategy;
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public Map<String, Object> getContext() {
        return Collections.unmodifiableMap(context);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getStepCount() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public ExecutionStep getStep(String stepId) {
        return steps.stream()
                .filter(step -> step.getId().equals(stepId))
                .findFirst()
                .orElse(null);
    }

    public ExecutionStep getFirstStep() {
        return steps.isEmpty() ? null : steps.get(0);
    }

    public ExecutionStep getLastStep() {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }

    public List<ExecutionStep> getIndependentSteps() {
        return steps.stream()
                .filter(step -> !step.hasDependencies())
                .toList();
    }

    public List<ExecutionStep> getStepsDependingOn(String stepId) {
        return steps.stream()
                .filter(step -> step.getDependencies().contains(stepId))
                .toList();
    }

    public IntentType getIntentType() {
        return intent != null ? intent.getType() : IntentType.UNKNOWN;
    }

    public String getVariableAsString(String key) {
        Object value = variables.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExecutionPlan empty(IntentResult intent) {
        return builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .build();
    }

    public static ExecutionPlan singleStep(IntentResult intent, ExecutionStep step) {
        return builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(step)
                .build();
    }

    @Override
    public String toString() {
        return "ExecutionPlan{" +
                "id='" + id + '\'' +
                ", intent=" + (intent != null ? intent.getType() : "null") +
                ", stepCount=" + steps.size() +
                ", strategy=" + strategy +
                '}';
    }

    public static class Builder {
        private String id;
        private IntentResult intent;
        private List<ExecutionStep> steps = new ArrayList<>();
        private ExecutionStrategy strategy = ExecutionStrategy.SEQUENTIAL;
        private Map<String, Object> variables = new HashMap<>();
        private Map<String, Object> context = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder intent(IntentResult intent) {
            this.intent = intent;
            return this;
        }

        public Builder steps(List<ExecutionStep> steps) {
            this.steps = steps;
            return this;
        }

        public Builder step(ExecutionStep step) {
            this.steps.add(step);
            return this;
        }

        public Builder strategy(ExecutionStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder variable(String key, Object value) {
            this.variables.put(key, value);
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder contextEntry(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public ExecutionPlan build() {
            if (id == null || id.isEmpty()) {
                id = "plan-" + System.currentTimeMillis();
            }
            return new ExecutionPlan(this);
        }
    }
}
