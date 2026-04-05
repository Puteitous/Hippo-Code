package com.example.agent.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionStep {

    private final String id;
    private final StepType type;
    private final String description;
    private final String toolName;
    private final Map<String, Object> arguments;
    private final List<String> dependencies;
    private final String condition;
    private final int retryCount;
    private final long timeoutMs;
    private final Map<String, Object> metadata;

    private ExecutionStep(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.description = builder.description;
        this.toolName = builder.toolName;
        this.arguments = builder.arguments != null ? new HashMap<>(builder.arguments) : new HashMap<>();
        this.dependencies = builder.dependencies != null ? new ArrayList<>(builder.dependencies) : new ArrayList<>();
        this.condition = builder.condition;
        this.retryCount = builder.retryCount;
        this.timeoutMs = builder.timeoutMs;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public StepType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return Collections.unmodifiableMap(arguments);
    }

    public List<String> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public String getCondition() {
        return condition;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArgument(String key, Class<T> type) {
        Object value = arguments.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public String getArgumentAsString(String key) {
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }

    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExecutionStep llmCall(String id, String description) {
        return builder()
                .id(id)
                .type(StepType.LLM_CALL)
                .description(description)
                .build();
    }

    public static ExecutionStep toolCall(String id, String toolName, String description) {
        return builder()
                .id(id)
                .type(StepType.TOOL_CALL)
                .toolName(toolName)
                .description(description)
                .build();
    }

    @Override
    public String toString() {
        return "ExecutionStep{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", description='" + description + '\'' +
                ", toolName='" + toolName + '\'' +
                ", dependencies=" + dependencies +
                '}';
    }

    public static class Builder {
        private String id;
        private StepType type = StepType.LLM_CALL;
        private String description;
        private String toolName;
        private Map<String, Object> arguments = new HashMap<>();
        private List<String> dependencies = new ArrayList<>();
        private String condition;
        private int retryCount = 0;
        private long timeoutMs = 60000;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(StepType type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder argument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder dependsOn(String stepId) {
            this.dependencies.add(stepId);
            return this;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public ExecutionStep build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Step id is required");
            }
            return new ExecutionStep(this);
        }
    }
}
