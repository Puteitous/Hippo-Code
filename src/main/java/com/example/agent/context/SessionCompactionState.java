package com.example.agent.context;

public class SessionCompactionState {

    private String lastSummarizedMessageId;
    private int compactionCount;
    private long lastCompactionTime;
    private long lastExtractionTime;
    private long firstMessageTimestamp;

    public SessionCompactionState() {
        this.compactionCount = 0;
        this.lastCompactionTime = 0;
        this.lastExtractionTime = 0;
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

    public void reset() {
        this.lastSummarizedMessageId = null;
        this.compactionCount = 0;
        this.lastCompactionTime = 0;
        this.lastExtractionTime = 0;
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
