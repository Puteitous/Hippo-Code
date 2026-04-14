package com.example.agent.core.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Health {
    private final HealthStatus status;
    private final Map<String, Object> details;
    private final Instant timestamp;

    private Health(Builder builder) {
        this.status = builder.status;
        this.details = new LinkedHashMap<>(builder.details);
        this.timestamp = Instant.now();
    }

    public static Builder up() {
        return new Builder(HealthStatus.UP);
    }

    public static Builder down() {
        return new Builder(HealthStatus.DOWN);
    }

    public static Builder degraded() {
        return new Builder(HealthStatus.DEGRADED);
    }

    public static Builder unknown() {
        return new Builder(HealthStatus.UNKNOWN);
    }

    public static Builder status(HealthStatus status) {
        return new Builder(status);
    }

    public HealthStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return new LinkedHashMap<>(details);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isUp() {
        return status == HealthStatus.UP;
    }

    public boolean isDown() {
        return status == HealthStatus.DOWN;
    }

    public static final class Builder {
        private final HealthStatus status;
        private final Map<String, Object> details = new LinkedHashMap<>();

        private Builder(HealthStatus status) {
            this.status = status;
        }

        public Builder withDetail(String key, Object value) {
            details.put(key, value);
            return this;
        }

        public Builder withException(Exception ex) {
            details.put("error", ex.getClass().getName());
            details.put("message", ex.getMessage());
            return this;
        }

        public Health build() {
            return new Health(this);
        }
    }
}
