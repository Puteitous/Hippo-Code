package com.example.agent.plan;

import com.example.agent.console.AgentUi;
import com.example.agent.core.AgentContext;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;
import com.example.agent.tools.ToolRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionContext {

    private final AgentContext agentContext;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConversationManager conversationManager;
    private final AgentUi ui;
    private final List<Message> conversationHistory;
    private final Map<String, Object> state;

    private ExecutionContext(Builder builder) {
        this.agentContext = builder.agentContext;
        this.llmClient = builder.llmClient;
        this.toolRegistry = builder.toolRegistry;
        this.conversationManager = builder.conversationManager;
        this.ui = builder.ui;
        this.conversationHistory = builder.conversationHistory;
        this.state = builder.state != null ? new HashMap<>(builder.state) : new HashMap<>();
    }

    public AgentContext getAgentContext() {
        return agentContext;
    }

    public LlmClient getLlmClient() {
        return llmClient;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public AgentUi getUi() {
        return ui;
    }

    public List<Message> getConversationHistory() {
        return conversationHistory;
    }

    public Map<String, Object> getState() {
        return state;
    }

    @SuppressWarnings("unchecked")
    public <T> T getStateValue(String key, Class<T> type) {
        Object value = state.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public void setStateValue(String key, Object value) {
        state.put(key, value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AgentContext agentContext;
        private LlmClient llmClient;
        private ToolRegistry toolRegistry;
        private ConversationManager conversationManager;
        private AgentUi ui;
        private List<Message> conversationHistory;
        private Map<String, Object> state = new HashMap<>();

        public Builder agentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
            return this;
        }

        public Builder llmClient(LlmClient llmClient) {
            this.llmClient = llmClient;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder conversationManager(ConversationManager conversationManager) {
            this.conversationManager = conversationManager;
            return this;
        }

        public Builder ui(AgentUi ui) {
            this.ui = ui;
            return this;
        }

        public Builder conversationHistory(List<Message> conversationHistory) {
            this.conversationHistory = conversationHistory;
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

        public ExecutionContext build() {
            return new ExecutionContext(this);
        }
    }
}
