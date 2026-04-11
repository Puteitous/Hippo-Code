package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleConfig {

    public static final String DEFAULT_RULES_FILE = ".hipporules";
    public static final String DEFAULT_MEMORY_FILE = "MEMORY.md";
    public static final int DEFAULT_MAX_TOKENS = 8000;

    @JsonProperty("rules_file")
    private String rulesFile = DEFAULT_RULES_FILE;

    @JsonProperty("memory_file")
    private String memoryFile = DEFAULT_MEMORY_FILE;

    @JsonProperty("max_tokens")
    private int maxTokens = DEFAULT_MAX_TOKENS;

    @JsonProperty("inject_at_startup")
    private boolean injectAtStartup = true;

    public RuleConfig() {
    }

    public String getRulesFile() {
        return rulesFile;
    }

    public void setRulesFile(String rulesFile) {
        this.rulesFile = rulesFile;
    }

    public String getMemoryFile() {
        return memoryFile;
    }

    public void setMemoryFile(String memoryFile) {
        this.memoryFile = memoryFile;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isInjectAtStartup() {
        return injectAtStartup;
    }

    public void setInjectAtStartup(boolean injectAtStartup) {
        this.injectAtStartup = injectAtStartup;
    }

    @Override
    public String toString() {
        return "RuleConfig{" +
                "rulesFile='" + rulesFile + '\'' +
                ", memoryFile='" + memoryFile + '\'' +
                ", maxTokens=" + maxTokens +
                ", injectAtStartup=" + injectAtStartup +
                '}';
    }
}
