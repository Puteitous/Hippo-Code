package com.example.agent.memory.model;

public enum MemoryPriority {

    PINNED(0, "钉住", Integer.MAX_VALUE),
    HIGH(1, "高优先级", 50),
    MEDIUM(2, "中优先级", 30),
    LOW(3, "低优先级", 10),
    EPHEMERAL(4, "临时", 3);

    private final int level;
    private final String displayName;
    private final int maxRetention;

    MemoryPriority(int level, String displayName, int maxRetention) {
        this.level = level;
        this.displayName = displayName;
        this.maxRetention = maxRetention;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxRetention() {
        return maxRetention;
    }

    public boolean isPinned() {
        return this == PINNED;
    }

    public boolean shouldSummarizeFirst() {
        return this == LOW || this == EPHEMERAL;
    }
}
