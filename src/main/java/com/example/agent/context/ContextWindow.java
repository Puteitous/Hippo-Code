package com.example.agent.context;

import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ContextWindow {

    private final TokenBudget budget;
    private final List<Message> messages;
    private final TokenEstimator tokenEstimator;
    private final List<Message> injectedWarnings;
    private boolean isRecalculating;

    public ContextWindow(int maxTokens, TokenEstimator tokenEstimator) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator must not be null");
        }
        this.budget = new TokenBudget(maxTokens);
        this.messages = new CopyOnWriteArrayList<>();
        this.tokenEstimator = tokenEstimator;
        this.injectedWarnings = new CopyOnWriteArrayList<>();
        this.isRecalculating = false;
    }

    public void addMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        messages.add(message);
        recalculateBudget();
    }

    public void addMessages(List<Message> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return;
        }
        newMessages.forEach(msg -> {
            if (msg != null) {
                messages.add(msg);
            }
        });
        recalculateBudget();
    }

    private void recalculateBudget() {
        if (isRecalculating) {
            return;
        }
        try {
            isRecalculating = true;
            doRecalculate();
        } finally {
            isRecalculating = false;
        }
    }

    private void doRecalculate() {
        List<Message> allMessages = new ArrayList<>();
        allMessages.addAll(messages);
        allMessages.addAll(injectedWarnings);
        
        int totalTokens = tokenEstimator.estimateConversationTokens(allMessages);
        budget.update(totalTokens);
    }

    public void injectWarning(Message warningMessage) {
        if (warningMessage == null) {
            return;
        }
        injectedWarnings.add(warningMessage);
        recalculateBudget();
    }

    public void clearInjectedWarnings() {
        injectedWarnings.clear();
        recalculateBudget();
    }

    public List<Message> getEffectiveMessages() {
        List<Message> result = new ArrayList<>(messages);
        result.addAll(injectedWarnings);
        
        return result.stream()
            .filter(m -> !m.isMemorySaved())
            .collect(Collectors.toList());
    }

    public List<Message> getAllMessagesForUI() {
        List<Message> result = new ArrayList<>(messages);
        result.addAll(injectedWarnings);
        return Collections.unmodifiableList(result);
    }

    public List<Message> getRawMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void replaceMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        recalculateBudget();
    }

    public void removeMessage(int index) {
        if (index < 0 || index >= messages.size()) {
            throw new IndexOutOfBoundsException(
                String.format("Cannot remove message at index %d, size is %d", 
                    index, messages.size())
            );
        }
        messages.remove(index);
        recalculateBudget();
    }

    public TokenBudget getBudget() {
        return budget;
    }

    public int size() {
        return messages.size();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public void clear() {
        messages.clear();
        injectedWarnings.clear();
        budget.reset();
    }

    public void forceRecalculate() {
        boolean wasRecalculating = isRecalculating;
        try {
            isRecalculating = false;
            recalculateBudget();
        } finally {
            isRecalculating = wasRecalculating;
        }
    }
}
