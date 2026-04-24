package com.example.agent.subagent;

import com.example.agent.domain.conversation.Conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SubAgentTask {
    private final String taskId;
    private final String parentTaskId;
    private final String description;
    private final AtomicReference<SubAgentStatus> status;
    private final Conversation conversation;
    private final List<String> outputLog;
    private final Instant createdAt;
    private Instant evictAfter;
    private String resultSummary;
    private Throwable error;
    private final CompletableFuture<SubAgentTask> completionFuture;

    public SubAgentTask(String description, Conversation conversation) {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.parentTaskId = null;
        this.description = description;
        this.status = new AtomicReference<>(SubAgentStatus.PENDING);
        this.conversation = conversation;
        this.outputLog = Collections.synchronizedList(new ArrayList<>());
        this.createdAt = Instant.now();
        this.evictAfter = Instant.now().plusSeconds(3600);
        this.completionFuture = new CompletableFuture<>();
    }

    public void markCompleted() {
        completionFuture.complete(this);
    }

    public void markFailed(Throwable e) {
        completionFuture.completeExceptionally(e);
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
}
