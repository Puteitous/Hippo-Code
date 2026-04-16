package com.example.agent.core.todo;

public enum TodoStatus {

    PENDING("pending", "⏳"),
    IN_PROGRESS("in_progress", "🔄"),
    COMPLETED("completed", "✅");

    private final String key;
    private final String icon;

    TodoStatus(String key, String icon) {
        this.key = key;
        this.icon = icon;
    }

    public String getKey() {
        return key;
    }

    public String getIcon() {
        return icon;
    }

    public static TodoStatus fromKey(String key) {
        if (key == null) {
            return PENDING;
        }
        for (TodoStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }
        return PENDING;
    }
}
