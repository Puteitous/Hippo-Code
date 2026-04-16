package com.example.agent.core.error;

public enum ErrorCode {

    FILE_NOT_FOUND("F001"),
    FILE_PERMISSION_ERROR("F002"),
    FILE_TOO_LARGE("F003"),
    FILE_READ_ERROR("F004"),
    FILE_WRITE_ERROR("F005"),

    LLM_CONNECTION_ERROR("L001"),
    LLM_TIMEOUT("L002"),
    LLM_AUTH_ERROR("L003"),
    LLM_RATE_LIMIT("L004"),

    SYSTEM_OUT_OF_MEMORY("S001"),
    SYSTEM_DISK_FULL("S002"),
    SYSTEM_CONFIG_ERROR("S003"),

    BLOCKED_EDIT_COUNT("B001"),
    BLOCKED_DANGEROUS_COMMAND("B002");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return ErrorCodeMessages.getMessage(this);
    }

    public String getSuggestion() {
        return ErrorCodeMessages.getSuggestion(this);
    }
}
