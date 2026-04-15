package com.example.agent.progress;

public enum ExecutionStage {

    ANALYZING("分析中", "🔍"),

    EXECUTING("执行中", "🛠️"),

    GENERATING("生成中", "✨");

    private final String displayName;
    private final String emoji;

    ExecutionStage(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String format() {
        return emoji + " " + displayName;
    }
}
