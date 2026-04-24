package com.example.agent.subagent.event;

public class SubAgentStartedEvent extends SubAgentEvent {
    public SubAgentStartedEvent(String taskId, String description) {
        super(taskId, description);
    }
}
