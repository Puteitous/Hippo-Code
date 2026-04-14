package com.example.agent.core.event;

public record MessageEvent(
        MessageType type,
        int tokenCount,
        int messageIndex
) implements Event {

    public enum MessageType {
        USER, ASSISTANT, SYSTEM, TOOL_RESULT
    }
}