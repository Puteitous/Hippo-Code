package com.example.agent.memory.model;

import com.example.agent.llm.model.Message;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

public class PrioritizedMessage {

    private final Message message;
    private final MemoryPriority priority;
    private final Set<MemoryTag> tags;
    private final int importanceScore;
    private final boolean compressible;
    private final Instant timestamp;

    private PrioritizedMessage(Message message, MemoryPriority priority, Set<MemoryTag> tags,
                               int importanceScore, boolean compressible, Instant timestamp) {
        this.message = message;
        this.priority = priority;
        this.tags = tags != null ? EnumSet.copyOf(tags) : EnumSet.noneOf(MemoryTag.class);
        this.importanceScore = Math.clamp(importanceScore, 0, 100);
        this.compressible = compressible;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static Builder builder(Message message) {
        return new Builder(message);
    }

    public Message getMessage() {
        return message;
    }

    public MemoryPriority getPriority() {
        return priority;
    }

    public Set<MemoryTag> getTags() {
        return EnumSet.copyOf(tags);
    }

    public boolean hasTag(MemoryTag tag) {
        return tags.contains(tag);
    }

    public int getImportanceScore() {
        return importanceScore;
    }

    public boolean isCompressible() {
        return compressible;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int calculateRetentionScore() {
        int baseScore = (4 - priority.getLevel()) * 25;
        int tagBonus = (tags.contains(MemoryTag.USER_REQUIREMENT) ? 20 : 0)
                + (tags.contains(MemoryTag.ERROR_INFO) ? 15 : 0)
                + (tags.contains(MemoryTag.IMPORTANT_DECISION) ? 10 : 0);
        return Math.clamp(baseScore + importanceScore + tagBonus, 0, 100);
    }

    public static class Builder {

        private final Message message;
        private MemoryPriority priority = MemoryPriority.MEDIUM;
        private Set<MemoryTag> tags = EnumSet.noneOf(MemoryTag.class);
        private int importanceScore = 50;
        private boolean compressible = false;
        private Instant timestamp;

        private Builder(Message message) {
            this.message = message;
        }

        public Builder priority(MemoryPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder tag(MemoryTag tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder importance(int score) {
            this.importanceScore = score;
            return this;
        }

        public Builder compressible(boolean compressible) {
            this.compressible = compressible;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PrioritizedMessage build() {
            return new PrioritizedMessage(message, priority, tags, importanceScore, compressible, timestamp);
        }
    }
}
