package com.example.agent.llm.retry;

import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;

public class RetryPolicy {

    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;

    public RetryPolicy() {
        this(3, 1000, 2.0, 10000);
    }

    public RetryPolicy(int maxRetries, long initialDelayMs, double backoffMultiplier, long maxDelayMs) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries不能为负数: " + maxRetries);
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException("initialDelayMs不能为负数: " + initialDelayMs);
        }
        if (backoffMultiplier <= 0) {
            throw new IllegalArgumentException("backoffMultiplier必须为正数: " + backoffMultiplier);
        }
        if (maxDelayMs < 0) {
            throw new IllegalArgumentException("maxDelayMs不能为负数: " + maxDelayMs);
        }
        if (initialDelayMs > maxDelayMs) {
            throw new IllegalArgumentException("initialDelayMs不能大于maxDelayMs");
        }
        
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public long getDelayMs(int attempt) {
        if (attempt < 0) {
            return initialDelayMs;
        }
        
        // 防止指数爆炸和溢出
        if (attempt > 30) {  // 限制最大attempt，防止Math.pow溢出
            return maxDelayMs;
        }
        
        try {
            double delay = initialDelayMs * Math.pow(backoffMultiplier, attempt);
            // 检查是否溢出
            if (delay > maxDelayMs || delay < 0 || Double.isInfinite(delay) || Double.isNaN(delay)) {
                return maxDelayMs;
            }
            return Math.min((long) delay, maxDelayMs);
        } catch (Exception e) {
            return maxDelayMs;
        }
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
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries不能为负数: " + maxRetries);
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            if (initialDelayMs < 0) {
                throw new IllegalArgumentException("initialDelayMs不能为负数: " + initialDelayMs);
            }
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier <= 0) {
                throw new IllegalArgumentException("backoffMultiplier必须为正数: " + backoffMultiplier);
            }
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder maxDelayMs(long maxDelayMs) {
            if (maxDelayMs < 0) {
                throw new IllegalArgumentException("maxDelayMs不能为负数: " + maxDelayMs);
            }
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialDelayMs, backoffMultiplier, maxDelayMs);
        }
    }
}