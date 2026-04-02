package com.example.agent.llm;


public class LlmConnectionException extends LlmException {

    private final String baseUrl;

    public LlmConnectionException(String message, String baseUrl) {
        super(message);
        this.baseUrl = baseUrl;
    }

    public LlmConnectionException(String message, String baseUrl, Throwable cause) {
        super(message, cause);
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}