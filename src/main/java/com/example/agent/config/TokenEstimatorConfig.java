package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenEstimatorConfig {

    public static final String TYPE_TIKTOKEN = "tiktoken";
    public static final String TYPE_SIMPLE = "simple";
    public static final String DEFAULT_TYPE = TYPE_TIKTOKEN;
    public static final int DEFAULT_CACHE_MAX_SIZE = 1000;

    private String type = DEFAULT_TYPE;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("cache_enabled")
    private boolean cacheEnabled = true;
    
    @JsonProperty("cache_max_size")
    private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;

    public TokenEstimatorConfig() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    @Override
    public String toString() {
        return "TokenEstimatorConfig{" +
                "type='" + type + '\'' +
                ", model='" + model + '\'' +
                ", cacheEnabled=" + cacheEnabled +
                ", cacheMaxSize=" + cacheMaxSize +
                '}';
    }
}