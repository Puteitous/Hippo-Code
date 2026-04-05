package com.example.agent.intent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IntentResult {

    private final IntentType type;
    private final double confidence;
    private final Map<String, Object> entities;
    private final String reasoning;
    private final String clarifiedIntent;

    private IntentResult(Builder builder) {
        this.type = builder.type;
        this.confidence = builder.confidence;
        this.entities = builder.entities != null ? new HashMap<>(builder.entities) : new HashMap<>();
        this.reasoning = builder.reasoning;
        this.clarifiedIntent = builder.clarifiedIntent;
    }

    public IntentType getType() {
        return type;
    }

    public double getConfidence() {
        return confidence;
    }

    public Map<String, Object> getEntities() {
        return Collections.unmodifiableMap(entities);
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getClarifiedIntent() {
        return clarifiedIntent;
    }

    @SuppressWarnings("unchecked")
    public <T> T getEntity(String key, Class<T> type) {
        Object value = entities.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public String getEntityAsString(String key) {
        Object value = entities.get(key);
        return value != null ? value.toString() : null;
    }

    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean isLowConfidence() {
        return confidence < 0.5;
    }

    public boolean needsClarification() {
        return type == IntentType.UNKNOWN || isLowConfidence();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static IntentResult unknown() {
        return builder()
                .type(IntentType.UNKNOWN)
                .confidence(0.0)
                .reasoning("无法识别用户意图")
                .build();
    }

    public static IntentResult of(IntentType type, double confidence) {
        return builder()
                .type(type)
                .confidence(confidence)
                .build();
    }

    @Override
    public String toString() {
        return "IntentResult{" +
                "type=" + type +
                ", confidence=" + String.format("%.2f", confidence) +
                ", entities=" + entities.keySet() +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }

    public static class Builder {
        private IntentType type = IntentType.UNKNOWN;
        private double confidence = 0.0;
        private Map<String, Object> entities = new HashMap<>();
        private String reasoning;
        private String clarifiedIntent;

        public Builder type(IntentType type) {
            this.type = type;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder entities(Map<String, Object> entities) {
            this.entities = entities;
            return this;
        }

        public Builder entity(String key, Object value) {
            this.entities.put(key, value);
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder clarifiedIntent(String clarifiedIntent) {
            this.clarifiedIntent = clarifiedIntent;
            return this;
        }

        public IntentResult build() {
            return new IntentResult(this);
        }
    }
}
