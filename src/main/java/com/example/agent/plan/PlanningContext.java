package com.example.agent.plan;

import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanningContext {

    private final String userInput;
    private final List<Message> conversationHistory;
    private final ConversationManager conversationManager;
    private final Map<String, Object> state;
    private final int currentRound;

    private PlanningContext(Builder builder) {
        this.userInput = builder.userInput;
        this.conversationHistory = builder.conversationHistory;
        this.conversationManager = builder.conversationManager;
        this.state = builder.state != null ? new HashMap<>(builder.state) : new HashMap<>();
        this.currentRound = builder.currentRound;
    }

    public String getUserInput() {
        return userInput;
    }

    public List<Message> getConversationHistory() {
        return conversationHistory != null ? Collections.unmodifiableList(conversationHistory) : Collections.emptyList();
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public Map<String, Object> getState() {
        return Collections.unmodifiableMap(state);
    }

    public int getCurrentRound() {
        return currentRound;
    }

    @SuppressWarnings("unchecked")
    public <T> T getStateValue(String key, Class<T> type) {
        Object value = state.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public String getStateAsString(String key) {
        Object value = state.get(key);
        return value != null ? value.toString() : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PlanningContext of(String userInput, List<Message> history) {
        return builder()
                .userInput(userInput)
                .conversationHistory(history)
                .build();
    }

    public static class Builder {
        private String userInput;
        private List<Message> conversationHistory;
        private ConversationManager conversationManager;
        private Map<String, Object> state = new HashMap<>();
        private int currentRound = 0;

        public Builder userInput(String userInput) {
            this.userInput = userInput;
            return this;
        }

        public Builder conversationHistory(List<Message> conversationHistory) {
            this.conversationHistory = conversationHistory;
            return this;
        }

        public Builder conversationManager(ConversationManager conversationManager) {
            this.conversationManager = conversationManager;
            return this;
        }

        public Builder state(Map<String, Object> state) {
            this.state = state;
            return this;
        }

        public Builder stateValue(String key, Object value) {
            this.state.put(key, value);
            return this;
        }

        public Builder currentRound(int currentRound) {
            this.currentRound = currentRound;
            return this;
        }

        public PlanningContext build() {
            return new PlanningContext(this);
        }
    }
}
