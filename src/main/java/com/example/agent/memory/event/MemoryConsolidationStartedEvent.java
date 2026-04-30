package com.example.agent.memory;

import com.example.agent.core.event.Event;

import java.time.Instant;

/**
 * 记忆整理开始事件
 */
public class MemoryConsolidationStartedEvent implements Event {
    private final int pendingSessionCount;
    private final Instant timestamp;

    public MemoryConsolidationStartedEvent(int pendingSessionCount, Instant timestamp) {
        this.pendingSessionCount = pendingSessionCount;
        this.timestamp = timestamp;
    }

    public int getPendingSessionCount() {
        return pendingSessionCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "MemoryConsolidationStartedEvent{" +
            "pendingSessionCount=" + pendingSessionCount +
            ", timestamp=" + timestamp +
            '}';
    }
}
