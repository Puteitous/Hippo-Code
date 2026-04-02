package com.example.agent.llm;

public class RetryPolicy {

    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;

    public RetryPolicy() {
        this(3, 1000, 2.0, 10000);
    }

    public RetryPolicy(int maxRetries, long initialDelayMs, double backoffMultiplier, long maxDelayMs) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getDelayMs(int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attempt));
        return Math.min(delay, maxDelayMs);
    }

    public boolean shouldRetry(LlmException exception, int attempt) {
        if (attempt >= maxRetries) {
            return false;
        }

        if (exception instanceof LlmTimeoutException) {
            return true;
        }

        if (exception instanceof LlmConnectionException) {
            return true;
        }

        if (exception instanceof LlmApiException) {
            LlmApiException apiException = (LlmApiException) exception;
            return apiException.isServerError() || apiException.isRateLimited();
        }

        return false;
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 1.0, 0);
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxRetries = 3;
        private long initialDelayMs = 1000;
        private double backoffMultiplier = 2.0;
        private long maxDelayMs = 10000;

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialDelayMs, backoffMultiplier, maxDelayMs);
        }
    }
}