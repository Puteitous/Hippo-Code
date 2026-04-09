package com.example.agent.context.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContextConfig {

    public static final int DEFAULT_MAX_TOKENS = 30000;
    public static final int DEFAULT_MAX_MESSAGES = 20;
    public static final int DEFAULT_KEEP_RECENT_TURNS = 6;
    public static final int DEFAULT_TOOL_RESULT_MAX_TOKENS = 2000;
    public static final String DEFAULT_POLICY = "simple";

    @JsonProperty("max_tokens")
    private int maxTokens = DEFAULT_MAX_TOKENS;

    @JsonProperty("max_messages")
    private int maxMessages = DEFAULT_MAX_MESSAGES;

    @JsonProperty("keep_recent_turns")
    private int keepRecentTurns = DEFAULT_KEEP_RECENT_TURNS;

    @JsonProperty("tool_result")
    private ToolResultConfig toolResult = new ToolResultConfig();

    // 策略配置
    @JsonProperty("policy")
    private String policy = DEFAULT_POLICY;

    @JsonProperty("hot_memory")
    private HotMemoryConfig hotMemory = new HotMemoryConfig();

    @JsonProperty("warm_memory")
    private WarmMemoryConfig warmMemory = new WarmMemoryConfig();

    @JsonProperty("cold_memory")
    private ColdMemoryConfig coldMemory = new ColdMemoryConfig();

    public ContextConfig() {
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getKeepRecentTurns() {
        return keepRecentTurns;
    }

    public void setKeepRecentTurns(int keepRecentTurns) {
        this.keepRecentTurns = keepRecentTurns;
    }

    public ToolResultConfig getToolResult() {
        return toolResult;
    }

    public void setToolResult(ToolResultConfig toolResult) {
        this.toolResult = toolResult;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public HotMemoryConfig getHotMemory() {
        return hotMemory;
    }

    public void setHotMemory(HotMemoryConfig hotMemory) {
        this.hotMemory = hotMemory;
    }

    public WarmMemoryConfig getWarmMemory() {
        return warmMemory;
    }

    public void setWarmMemory(WarmMemoryConfig warmMemory) {
        this.warmMemory = warmMemory;
    }

    public ColdMemoryConfig getColdMemory() {
        return coldMemory;
    }

    public void setColdMemory(ColdMemoryConfig coldMemory) {
        this.coldMemory = coldMemory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolResultConfig {
        
        public static final String DEFAULT_TRUNCATE_STRATEGY = "tail";

        @JsonProperty("max_tokens")
        private int maxTokens = DEFAULT_TOOL_RESULT_MAX_TOKENS;

        @JsonProperty("truncate_strategy")
        private String truncateStrategy = DEFAULT_TRUNCATE_STRATEGY;

        public ToolResultConfig() {
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getTruncateStrategy() {
            return truncateStrategy;
        }

        public void setTruncateStrategy(String truncateStrategy) {
            this.truncateStrategy = truncateStrategy;
        }
    }

    @Override
    public String toString() {
        return "ContextConfig{" +
                "maxTokens=" + maxTokens +
                ", maxMessages=" + maxMessages +
                ", keepRecentTurns=" + keepRecentTurns +
                ", toolResult=" + toolResult +
                ", policy='" + policy + '\'' +
                ", hotMemory=" + hotMemory +
                ", warmMemory=" + warmMemory +
                ", coldMemory=" + coldMemory +
                '}';
    }

    // HotMemory 配置
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HotMemoryConfig {
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
            return "HotMemoryConfig{" +
                    "rulesFile='" + rulesFile + '\'' +
                    ", memoryFile='" + memoryFile + '\'' +
                    ", maxTokens=" + maxTokens +
                    ", injectAtStartup=" + injectAtStartup +
                    '}';
        }
    }

    // WarmMemory 配置
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WarmMemoryConfig {
        public static final int DEFAULT_MAX_FILE_TOKENS = 4000;
        public static final int DEFAULT_CACHE_TTL_SECONDS = 300;

        @JsonProperty("max_file_tokens")
        private int maxFileTokens = DEFAULT_MAX_FILE_TOKENS;

        @JsonProperty("cache_ttl_seconds")
        private int cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;

        public int getMaxFileTokens() {
            return maxFileTokens;
        }

        public void setMaxFileTokens(int maxFileTokens) {
            this.maxFileTokens = maxFileTokens;
        }

        public int getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(int cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        @Override
        public String toString() {
            return "WarmMemoryConfig{" +
                    "maxFileTokens=" + maxFileTokens +
                    ", cacheTtlSeconds=" + cacheTtlSeconds +
                    '}';
        }
    }

    // ColdMemory 配置
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColdMemoryConfig {
        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_MAX_RESULTS = 3;
        public static final int DEFAULT_MAX_TOKENS = 5000;

        @JsonProperty("enabled")
        private boolean enabled = DEFAULT_ENABLED;

        @JsonProperty("max_results")
        private int maxResults = DEFAULT_MAX_RESULTS;

        @JsonProperty("max_tokens")
        private int maxTokens = DEFAULT_MAX_TOKENS;

        public ColdMemoryConfig() {
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
            return "ColdMemoryConfig{" +
                    "enabled=" + enabled +
                    ", maxResults=" + maxResults +
                    ", maxTokens=" + maxTokens +
                    '}';
        }
    }
}
