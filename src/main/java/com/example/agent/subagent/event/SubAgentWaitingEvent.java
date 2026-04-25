package com.example.agent.subagent.event;

import java.util.List;

public class SubAgentWaitingEvent extends SubAgentEvent {
    private final List<String> dependsOn;

    public SubAgentWaitingEvent(String taskId, String description, List<String> dependsOn) {
        super(taskId, description);
        this.dependsOn = dependsOn;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }
}
