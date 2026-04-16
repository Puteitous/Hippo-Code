package com.example.agent.prompt.model;

public enum TaskMode {

    CHAT("chat", "聊天对话模式"),
    CODING("coding", "通用编程模式"),
    RESEARCH("research", "代码研究模式"),
    REFACTOR("refactor", "重构模式"),
    DEBUG("debug", "调试模式");

    private final String key;
    private final String displayName;

    TaskMode(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PromptType getDefaultBasePromptType() {
        return switch (this) {
            case CHAT -> PromptType.BASE_CHAT;
            case RESEARCH -> PromptType.BASE_RESEARCH;
            default -> PromptType.BASE_CODING;
        };
    }
}
