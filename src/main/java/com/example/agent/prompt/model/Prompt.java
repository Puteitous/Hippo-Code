package com.example.agent.prompt.model;

public class Prompt {

    private final PromptType type;
    private final String content;
    private final PromptVersion version;
    private final boolean enabled;
    private final int priority;
    private final int estimatedTokens;

    private Prompt(PromptType type, String content, PromptVersion version,
                   boolean enabled, int priority, int estimatedTokens) {
        this.type = type;
        this.content = content != null ? content : "";
        this.version = version != null ? version : PromptVersion.DEFAULT;
        this.enabled = enabled;
        this.priority = priority;
        this.estimatedTokens = estimatedTokens > 0 ? estimatedTokens : estimateTokensFromContent();
    }

    public static Builder builder() {
        return new Builder();
    }

    public PromptType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public PromptVersion getVersion() {
        return version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }

    private int estimateTokensFromContent() {
        return content.length() / 4;
    }

    public String mergeWith(Prompt other) {
        return this.content + "\n\n" + other.getContent();
    }

    public static class Builder {

        private PromptType type;
        private String content;
        private PromptVersion version = PromptVersion.DEFAULT;
        private boolean enabled = true;
        private int priority = 0;
        private int estimatedTokens;

        public Builder type(PromptType type) {
            this.type = type;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder version(String version) {
            this.version = PromptVersion.fromString(version);
            return this;
        }

        public Builder version(PromptVersion version) {
            this.version = version;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder estimatedTokens(int estimatedTokens) {
            this.estimatedTokens = estimatedTokens;
            return this;
        }

        public Prompt build() {
            if (type == null) {
                throw new IllegalStateException("PromptType must be set");
            }
            return new Prompt(type, content, version, enabled, priority, estimatedTokens);
        }
    }
}
