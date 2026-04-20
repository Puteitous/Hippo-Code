package com.example.agent.memory;

import java.time.Instant;
import java.util.Set;

public class MemoryEntry {

    private final String id;
    private final String content;
    private final MemoryType type;
    private final Set<String> tags;
    private final double importance;
    private final Instant createdAt;
    private final Instant lastAccessed;
    private final int accessCount;

    public MemoryEntry(String id, String content, MemoryType type, Set<String> tags, double importance) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.tags = tags;
        this.importance = importance;
        this.createdAt = Instant.now();
        this.lastAccessed = Instant.now();
        this.accessCount = 0;
    }

    public enum MemoryType {
        FACT,
        USER_PREFERENCE,
        TECHNICAL_CONTEXT,
        DECISION,
        LESSON_LEARNED,
        PROJECT_CONTEXT
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public MemoryType getType() { return type; }
    public Set<String> getTags() { return tags; }
    public double getImportance() { return importance; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessed() { return lastAccessed; }
    public int getAccessCount() { return accessCount; }

    public double calculateRelevance(String query) {
        double relevance = 0;
        String queryLower = query.toLowerCase();
        String contentLower = content.toLowerCase();

        for (String tag : tags) {
            if (queryLower.contains(tag.toLowerCase())) {
                relevance += 0.3;
            }
        }

        String[] queryWords = queryLower.split("\\s+");
        for (String word : queryWords) {
            if (contentLower.contains(word)) {
                relevance += 0.1;
            }
        }

        relevance += importance * 0.5;

        return Math.min(relevance, 1.0);
    }
}
