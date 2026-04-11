package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexConfig {

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MAX_RESULTS = 3;
    public static final int DEFAULT_MAX_TOKENS = 5000;

    private boolean enabled = DEFAULT_ENABLED;

    @JsonProperty("max_results")
    private int maxResults = DEFAULT_MAX_RESULTS;

    @JsonProperty("max_tokens")
    private int maxTokens = DEFAULT_MAX_TOKENS;

    public IndexConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public String toString() {
        return "IndexConfig{" +
                "enabled=" + enabled +
                ", maxResults=" + maxResults +
                ", maxTokens=" + maxTokens +
                '}';
    }
}
