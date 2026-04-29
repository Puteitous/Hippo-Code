package com.example.agent.orchestrator.model;

public class ToolRetryPolicy {

    public static final ToolRetryPolicy NO_RETRY = new ToolRetryPolicy(1, 0, false);

    private final int maxRetries;
    private final int delayMs;
    private final boolean idempotent;

    public ToolRetryPolicy(int maxRetries, int delayMs, boolean idempotent) {
        this.maxRetries = maxRetries;
        this.delayMs = delayMs;
        this.idempotent = idempotent;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public boolean shouldRetry() {
        return maxRetries > 1 && idempotent;
    }
}
