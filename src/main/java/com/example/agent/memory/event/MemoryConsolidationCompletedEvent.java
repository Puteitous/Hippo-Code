package com.example.agent.memory;

/**
 * 记忆整理完成事件
 */
public class MemoryConsolidationCompletedEvent {
    private final int consolidatedCount;
    private final int mergedCount;
    private final long durationMs;

    public MemoryConsolidationCompletedEvent(int consolidatedCount, int mergedCount, long durationMs) {
        this.consolidatedCount = consolidatedCount;
        this.mergedCount = mergedCount;
        this.durationMs = durationMs;
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
            "consolidatedCount=" + consolidatedCount +
            ", mergedCount=" + mergedCount +
            ", durationMs=" + durationMs +
            '}';
    }
}
