package com.example.agent.domain.conversation;

import com.example.agent.context.BlockingGuard;
import com.example.agent.context.ContextWindow;
import com.example.agent.context.TokenBudget;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Conversation {

    private final ContextWindow contextWindow;
    private final BlockingGuard blockingGuard;
    private final String sessionId;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
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
        lock.writeLock().lock();
        try {
            if (!blockingGuard.canAddMessage()) {
                throw new IllegalStateException(blockingGuard.getStatusMessage());
            }
            contextWindow.addMessage(message);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (!blockingGuard.canAddMessage()) {
                throw new IllegalStateException(blockingGuard.getStatusMessage());
            }
            contextWindow.addMessages(messages);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Message> getMessages() {
        lock.readLock().lock();
        try {
            return contextWindow.getRawMessages();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getMessageCount() {
        lock.readLock().lock();
        try {
            return contextWindow.getRawMessages().size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Message> getEffectiveMessages() {
        lock.readLock().lock();
        try {
            return contextWindow.getEffectiveMessages();
        } finally {
            lock.readLock().unlock();
        }
    }

    public TokenBudget getBudget() {
        lock.readLock().lock();
        try {
            return contextWindow.getBudget();
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getUsageRatio() {
        lock.readLock().lock();
        try {
            return contextWindow.getBudget().getUsageRatio();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTokenCount() {
        lock.readLock().lock();
        try {
            return contextWindow.getBudget().getCurrentTokens();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            contextWindow.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceMessages(List<Message> newMessages) {
        lock.writeLock().lock();
        try {
            contextWindow.clearInjectedWarnings();
            contextWindow.replaceMessages(newMessages);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return contextWindow.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearInjectedWarnings() {
        lock.writeLock().lock();
        try {
            contextWindow.clearInjectedWarnings();
        } finally {
            lock.writeLock().unlock();
        }
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
