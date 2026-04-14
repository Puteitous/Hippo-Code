package com.example.agent.core.error;

public class AgentException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public AgentException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public AgentException(ErrorCode errorCode, String detail) {
        super(formatMessage(errorCode, detail));
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public AgentException(ErrorCode errorCode, String detail, Throwable cause) {
        super(formatMessage(errorCode, detail), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    private static String formatMessage(ErrorCode errorCode, String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            return String.format("[%s] %s", errorCode.getCode(), errorCode.getMessage());
        }
        return String.format("[%s] %s: %s", errorCode.getCode(), errorCode.getMessage(), detail);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetail() {
        return detail;
    }

    public String getSuggestion() {
        return errorCode.getSuggestion();
    }
}
