package com.example.agent.domain.conversation;

import com.example.agent.context.BlockingGuard;
import com.example.agent.context.ContextWindow;
import com.example.agent.context.TokenBudget;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;
import java.util.UUID;

public class Conversation {

    private final ContextWindow contextWindow;
    private final BlockingGuard blockingGuard;
    private final String sessionId;
    private String systemPrompt;

    public Conversation(int maxTokens, TokenEstimator tokenEstimator) {
        this(maxTokens, tokenEstimator, UUID.randomUUID().toString());
    }

    public Conversation(int maxTokens, TokenEstimator tokenEstimator, String sessionId) {
        this.sessionId = sessionId;
        this.contextWindow = new ContextWindow(maxTokens, tokenEstimator);
        this.blockingGuard = new BlockingGuard(contextWindow);
    }

    public void addMessage(Message message) {
        if (!blockingGuard.canAddMessage()) {
            throw new IllegalStateException(blockingGuard.getStatusMessage());
        }
        contextWindow.addMessage(message);
    }

    public void addMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (!blockingGuard.canAddMessage()) {
            throw new IllegalStateException(blockingGuard.getStatusMessage());
        }
        contextWindow.addMessages(messages);
    }

    public List<Message> getMessages() {
        return contextWindow.getRawMessages();
    }

    public int getMessageCount() {
        return contextWindow.getRawMessages().size();
    }

    public List<Message> getEffectiveMessages() {
        return contextWindow.getEffectiveMessages();
    }

    public TokenBudget getBudget() {
        return contextWindow.getBudget();
    }

    public double getUsageRatio() {
        return contextWindow.getBudget().getUsageRatio();
    }

    public int getTokenCount() {
        return contextWindow.getBudget().getCurrentTokens();
    }

    public void clear() {
        contextWindow.clear();
    }

    public void replaceMessages(List<Message> newMessages) {
        contextWindow.clearInjectedWarnings();
        contextWindow.replaceMessages(newMessages);
    }

    public int size() {
        return contextWindow.size();
    }

    public void clearInjectedWarnings() {
        contextWindow.clearInjectedWarnings();
    }

    public ContextWindow getContextWindow() {
        return contextWindow;
    }

    public BlockingGuard getBlockingGuard() {
        return blockingGuard;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public boolean shouldMarkForMemory(Message message) {
        return message.isUser() && message.getContent() != null 
            && message.getContent().length() > 20;
    }
}
