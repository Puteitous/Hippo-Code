package com.example.agent.context;

public class SessionCompactionState {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private String lastSummarizedMessageId;
    private int compactionCount;
    private long lastCompactionTime;
    private long lastExtractionTime;
    private long firstMessageTimestamp;
    private int consecutiveFailures;
    private boolean compactedInCurrentLoop;

    public SessionCompactionState() {
        this.compactionCount = 0;
        this.lastCompactionTime = 0;
        this.lastExtractionTime = 0;
        this.consecutiveFailures = 0;
        this.compactedInCurrentLoop = false;
    }

    public boolean canIncrementalCompact() {
        return hasValidSummaryBoundary();
    }

    public boolean hasValidSummaryBoundary() {
        return lastSummarizedMessageId != null 
            && !lastSummarizedMessageId.isEmpty();
    }

    public void recordCompaction() {
        this.compactionCount++;
        this.lastCompactionTime = System.currentTimeMillis();
    }

    public void recordMemoryExtraction(String lastMessageId) {
        this.lastSummarizedMessageId = lastMessageId;
        this.lastExtractionTime = System.currentTimeMillis();
    }

    public boolean shouldTryCompaction() {
        return consecutiveFailures < MAX_CONSECUTIVE_FAILURES
            && !compactedInCurrentLoop;
    }

    public void recordFailure() {
        consecutiveFailures++;
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
        compactedInCurrentLoop = true;
    }

    public void startNewQueryLoop() {
        compactedInCurrentLoop = false;
    }

    public void reset() {
        this.lastSummarizedMessageId = null;
        this.compactionCount = 0;
        this.lastCompactionTime = 0;
        this.lastExtractionTime = 0;
        this.consecutiveFailures = 0;
        this.compactedInCurrentLoop = false;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public boolean isCompactedInCurrentLoop() {
        return compactedInCurrentLoop;
    }

    public String getLastSummarizedMessageId() {
        return lastSummarizedMessageId;
    }

    public void setLastSummarizedMessageId(String lastSummarizedMessageId) {
        this.lastSummarizedMessageId = lastSummarizedMessageId;
    }

    public int getCompactionCount() {
        return compactionCount;
    }

    public long getLastCompactionTime() {
        return lastCompactionTime;
    }

    public long getLastExtractionTime() {
        return lastExtractionTime;
    }

    public long getFirstMessageTimestamp() {
        return firstMessageTimestamp;
    }

    public void setFirstMessageTimestamp(long firstMessageTimestamp) {
        this.firstMessageTimestamp = firstMessageTimestamp;
    }
}
