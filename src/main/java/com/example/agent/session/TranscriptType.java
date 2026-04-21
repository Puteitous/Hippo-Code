package com.example.agent.session;

public enum TranscriptType {
    USER("user"),
    ASSISTANT("assistant"),
    TOOL_RESULT("tool-result"),
    SYSTEM("system"),
    SUMMARY("summary"),
    COMPACT_BOUNDARY("compact-boundary"),
    CUSTOM_TITLE("custom-title"),
    TAG("tag"),
    UNKNOWN("unknown");

    private final String value;

    TranscriptType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TranscriptType fromString(String value) {
        for (TranscriptType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
