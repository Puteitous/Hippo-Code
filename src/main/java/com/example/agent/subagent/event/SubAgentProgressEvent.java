package com.example.agent.subagent.event;

public class SubAgentProgressEvent extends SubAgentEvent {
    private final String message;

    public SubAgentProgressEvent(String taskId, String description, String message) {
        super(taskId, description);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
