package com.example.agent.core;

import com.example.agent.llm.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ThinkingContext<T> {

    private final String systemPrompt;
    private final String userInput;
    private final Set<String> allowedTools;
    private final int maxRounds;
    private final Function<String, T> resultParser;
    private final List<Message> history;

    private ThinkingContext(Builder<T> builder) {
        this.systemPrompt = builder.systemPrompt;
        this.userInput = builder.userInput;
        this.allowedTools = builder.allowedTools;
        this.maxRounds = builder.maxRounds;
        this.resultParser = builder.resultParser;
        this.history = builder.history != null ? new ArrayList<>(builder.history) : new ArrayList<>();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserInput() {
        return userInput;
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public Function<String, T> getResultParser() {
        return resultParser;
    }

    public List<Message> getHistory() {
        return history;
    }

    public T parseResult(String content) {
        return resultParser.apply(content);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String systemPrompt;
        private String userInput;
        private Set<String> allowedTools;
        private int maxRounds = 3;
        private Function<String, T> resultParser;
        private List<Message> history;

        public Builder<T> systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder<T> userInput(String userInput) {
            this.userInput = userInput;
            return this;
        }

        public Builder<T> allowedTools(Set<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder<T> maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder<T> resultParser(Function<String, T> resultParser) {
            this.resultParser = resultParser;
            return this;
        }

        public Builder<T> history(List<Message> history) {
            this.history = history;
            return this;
        }

        public ThinkingContext<T> build() {
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                throw new IllegalArgumentException("systemPrompt cannot be empty");
            }
            if (resultParser == null) {
                throw new IllegalArgumentException("resultParser cannot be null");
            }
            return new ThinkingContext<>(this);
        }
    }
}
