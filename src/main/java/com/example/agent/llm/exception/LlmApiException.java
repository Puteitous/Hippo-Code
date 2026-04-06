package com.example.agent.llm.exception;

public class LlmApiException extends LlmException {

    private final int statusCode;
    private final String errorBody;

    public LlmApiException(String message, int statusCode) {
        super(message);
        if (statusCode < 0) {
            statusCode = 0;
        }
        this.statusCode = statusCode;
        this.errorBody = null;
    }

    public LlmApiException(String message, int statusCode, String errorBody) {
        super(message);
        if (statusCode < 0) {
            statusCode = 0;
        }
        this.statusCode = statusCode;
        this.errorBody = errorBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorBody() {
        return errorBody;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isAuthenticationError() {
        return statusCode == 401 || statusCode == 403;
    }
    
    public boolean isValidStatusCode() {
        return statusCode >= 100 && statusCode < 600;
    }
}