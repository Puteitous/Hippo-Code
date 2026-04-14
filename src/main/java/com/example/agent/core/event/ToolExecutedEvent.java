package com.example.agent.core.event;

public record ToolExecutedEvent(
        String toolName,
        boolean success,
        long durationMs,
        String errorMessage
) implements Event {}