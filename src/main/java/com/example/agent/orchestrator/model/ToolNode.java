package com.example.agent.orchestrator.model;

import com.example.agent.llm.model.ToolCall;

import java.util.HashSet;
import java.util.Set;

public class ToolNode {

    private final ToolCall toolCall;
    private final Set<ToolNode> dependencies = new HashSet<>();
    private ExecutionStatus status = ExecutionStatus.PENDING;
    private Object result;
    private Throwable error;
    private int retryCount = 0;

    public ToolNode(ToolCall toolCall) {
        this.toolCall = toolCall;
    }

    public boolean isRunnable() {
        return status == ExecutionStatus.PENDING
                && dependencies.stream().allMatch(ToolNode::isSuccess);
    }

    public void addDependency(ToolNode node) {
        if (node != this && !dependencies.contains(node)) {
            dependencies.add(node);
        }
    }

    public boolean dependsOn(ToolNode other) {
        return dependencies.contains(other);
    }

    public int getInDegree() {
        return (int) dependencies.stream()
                .filter(n -> !n.isSuccess())
                .count();
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public String getToolCallId() {
        return toolCall.getId();
    }

    public String getToolName() {
        return toolCall.getFunction() != null ? toolCall.getFunction().getName() : "";
    }

    public Set<ToolNode> getDependencies() {
        return new HashSet<>(dependencies);
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
