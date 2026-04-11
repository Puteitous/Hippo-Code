package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheConfig {

    public static final int DEFAULT_MAX_FILE_TOKENS = 4000;
    public static final int DEFAULT_CACHE_TTL_SECONDS = 300;

    @JsonProperty("max_file_tokens")
    private int maxFileTokens = DEFAULT_MAX_FILE_TOKENS;

    @JsonProperty("cache_ttl_seconds")
    private int cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;

    public CacheConfig() {
    }

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
        return "CacheConfig{" +
                "maxFileTokens=" + maxFileTokens +
                ", cacheTtlSeconds=" + cacheTtlSeconds +
                '}';
    }
}
