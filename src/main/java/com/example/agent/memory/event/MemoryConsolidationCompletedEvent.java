package com.example.agent.memory;

import com.example.agent.core.event.Event;

/**
 * 记忆整理完成事件
 */
public class MemoryConsolidationCompletedEvent implements Event {
    private final int sessionCount;
    private final int consolidatedCount;
    private final int mergedCount;
    private final long durationMs;

    public MemoryConsolidationCompletedEvent(int sessionCount, int consolidatedCount, int mergedCount, long durationMs) {
        this.sessionCount = sessionCount;
        this.consolidatedCount = consolidatedCount;
        this.mergedCount = mergedCount;
        this.durationMs = durationMs;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public int getConsolidatedCount() {
        return consolidatedCount;
    }

    public int getMergedCount() {
        return mergedCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String toString() {
        return "MemoryConsolidationCompletedEvent{" +
            "sessionCount=" + sessionCount +
            ", consolidatedCount=" + consolidatedCount +
            ", mergedCount=" + mergedCount +
            ", durationMs=" + durationMs +
            '}';
    }
}
