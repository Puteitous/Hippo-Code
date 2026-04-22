package com.example.agent.context;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SessionCompactionState {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final AtomicReference<String> lastSummarizedMessageId;
    private final AtomicInteger compactionCount;
    private final AtomicLong lastCompactionTime;
    private final AtomicLong lastExtractionTime;
    private final AtomicLong firstMessageTimestamp;
    private final AtomicInteger consecutiveFailures;
    private final AtomicBoolean compactedInCurrentLoop;

    public SessionCompactionState() {
        this.lastSummarizedMessageId = new AtomicReference<>(null);
        this.compactionCount = new AtomicInteger(0);
        this.lastCompactionTime = new AtomicLong(0);
        this.lastExtractionTime = new AtomicLong(0);
        this.firstMessageTimestamp = new AtomicLong(0);
        this.consecutiveFailures = new AtomicInteger(0);
        this.compactedInCurrentLoop = new AtomicBoolean(false);
    }

    public boolean canIncrementalCompact() {
        return hasValidSummaryBoundary();
    }

    public boolean hasValidSummaryBoundary() {
        String id = lastSummarizedMessageId.get();
        return id != null && !id.isEmpty();
    }

    public void recordCompaction() {
        this.compactionCount.incrementAndGet();
        this.lastCompactionTime.set(System.currentTimeMillis());
    }

    public void recordMemoryExtraction(String lastMessageId) {
        this.lastSummarizedMessageId.set(lastMessageId);
        this.lastExtractionTime.set(System.currentTimeMillis());
    }

    public boolean shouldTryCompaction() {
        return consecutiveFailures.get() < MAX_CONSECUTIVE_FAILURES
            && !compactedInCurrentLoop.get();
    }

    public void recordFailure() {
        consecutiveFailures.incrementAndGet();
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        compactedInCurrentLoop.set(true);
    }

    public void startNewQueryLoop() {
        compactedInCurrentLoop.set(false);
    }

    public void reset() {
        this.lastSummarizedMessageId.set(null);
        this.compactionCount.set(0);
        this.lastCompactionTime.set(0);
        this.lastExtractionTime.set(0);
        this.consecutiveFailures.set(0);
        this.compactedInCurrentLoop.set(false);
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public boolean isCompactedInCurrentLoop() {
        return compactedInCurrentLoop.get();
    }

    public String getLastSummarizedMessageId() {
        return lastSummarizedMessageId.get();
    }

    public void setLastSummarizedMessageId(String lastSummarizedMessageId) {
        this.lastSummarizedMessageId.set(lastSummarizedMessageId);
    }

    public int getCompactionCount() {
        return compactionCount.get();
    }

    public long getLastCompactionTime() {
        return lastCompactionTime.get();
    }

    public long getLastExtractionTime() {
        return lastExtractionTime.get();
    }

    public long getFirstMessageTimestamp() {
        return firstMessageTimestamp.get();
    }

    public void setFirstMessageTimestamp(long firstMessageTimestamp) {
        this.firstMessageTimestamp.set(firstMessageTimestamp);
    }
}
