package com.example.agent.mcp.exception;

public class McpConnectionException extends McpException {

    public McpConnectionException(String message) {
        super(message);
    }

    public McpConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
