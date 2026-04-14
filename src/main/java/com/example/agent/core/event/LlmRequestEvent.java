package com.example.agent.core.event;

public record LlmRequestEvent(
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        boolean success
) implements Event {}