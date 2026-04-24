package com.example.agent.subagent.event;

public class SubAgentFailedEvent extends SubAgentEvent {
    private final String errorMessage;

    public SubAgentFailedEvent(String taskId, String description, String errorMessage) {
        super(taskId, description);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
