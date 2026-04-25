package com.example.agent.subagent;

import com.example.agent.domain.conversation.Conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SubAgentTask {
    private final String taskId;
    private final String parentTaskId;
    private final String description;
    private final List<String> dependsOn;
    private final AtomicReference<SubAgentStatus> status;
    private final Conversation conversation;
    private final List<String> outputLog;
    private final Instant createdAt;
    private Instant evictAfter;
    private final int timeoutSeconds;
    private final AtomicBoolean cancelled;
    private String resultSummary;
    private Throwable error;
    private final CompletableFuture<SubAgentTask> completionFuture;

    public SubAgentTask(String description, Conversation conversation) {
        this(description, conversation, 300, null);
    }

    public SubAgentTask(String description, Conversation conversation, int timeoutSeconds) {
        this(description, conversation, timeoutSeconds, null);
    }

    public SubAgentTask(String description, Conversation conversation, int timeoutSeconds, List<String> dependsOn) {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.parentTaskId = null;
        this.description = description;
        this.dependsOn = dependsOn != null ? new ArrayList<>(dependsOn) : Collections.emptyList();
        this.status = new AtomicReference<>(SubAgentStatus.PENDING);
        this.conversation = conversation;
        this.outputLog = Collections.synchronizedList(new ArrayList<>());
        this.createdAt = Instant.now();
        this.timeoutSeconds = timeoutSeconds;
        this.evictAfter = Instant.now().plusSeconds(3600);
        this.cancelled = new AtomicBoolean(false);
        this.completionFuture = new CompletableFuture<>();
    }

    public void markCompleted() {
        completionFuture.complete(this);
    }

    public void markFailed(Throwable e) {
        completionFuture.completeExceptionally(e);
    }

    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            setStatus(SubAgentStatus.CANCELLED);
            addLog("任务已被取消");
            completionFuture.cancel(true);
            return true;
        }
        return false;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isTimeout() {
        return Instant.now().isAfter(createdAt.plusSeconds(timeoutSeconds));
    }

    public boolean shouldStopExecution() {
        return isCancelled() || isTimeout();
    }

    public SubAgentTask awaitCompletion(long timeout, TimeUnit unit) throws Exception {
        return completionFuture.get(timeout, unit);
    }

    public CompletableFuture<SubAgentTask> getCompletionFuture() {
        return completionFuture;
    }

    public void addLog(String message) {
        outputLog.add(message);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDescription() {
        return description;
    }

    public SubAgentStatus getStatus() {
        return status.get();
    }

    public void setStatus(SubAgentStatus status) {
        this.status.set(status);
    }

    public Conversation getConversation() {
        return conversation;
    }

    public List<String> getOutputLog() {
        return new ArrayList<>(outputLog);
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(evictAfter);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public List<String> getDependsOn() {
        return Collections.unmodifiableList(dependsOn);
    }

    public void setDependsOn(List<String> dependsOn) {
        if (dependsOn != null) {
            this.dependsOn.clear();
            this.dependsOn.addAll(dependsOn);
        }
    }

    public boolean hasDependencies() {
        return !dependsOn.isEmpty();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
