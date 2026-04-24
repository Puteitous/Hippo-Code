package com.example.agent.subagent.event;

import com.example.agent.core.event.Event;
import com.example.agent.subagent.SubAgentStatus;

public abstract class SubAgentEvent implements Event {
    private final String taskId;
    private final String description;

    protected SubAgentEvent(String taskId, String description) {
        this.taskId = taskId;
        this.description = description;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDescription() {
        return description;
    }
}
