package com.example.agent.mcp.exception;

public class McpTimeoutException extends McpException {

    public McpTimeoutException(String message) {
        super(message);
    }

    public McpTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
