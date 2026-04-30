package com.example.agent.memory;

import com.example.agent.core.event.Event;

/**
 * 记忆整理失败事件
 */
public class MemoryConsolidationFailedEvent implements Event {
    private final int consecutiveFailures;
    private final String errorMessage;

    public MemoryConsolidationFailedEvent(int consecutiveFailures, String errorMessage) {
        this.consecutiveFailures = consecutiveFailures;
        this.errorMessage = errorMessage;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "MemoryConsolidationFailedEvent{" +
            "consecutiveFailures=" + consecutiveFailures +
            ", errorMessage='" + errorMessage + '\'' +
            '}';
    }
}
