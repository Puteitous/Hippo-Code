package com.example.agent.mcp.exception;

public class McpProtocolException extends McpException {

    private final int code;

    public McpProtocolException(String message) {
        this(-32603, message);
    }

    public McpProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public McpProtocolException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
