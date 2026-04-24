package com.example.agent.subagent.event;

public class SubAgentCompletedEvent extends SubAgentEvent {
    private final String result;

    public SubAgentCompletedEvent(String taskId, String description, String result) {
        super(taskId, description);
        this.result = result;
    }

    public String getResult() {
        return result;
    }
}
