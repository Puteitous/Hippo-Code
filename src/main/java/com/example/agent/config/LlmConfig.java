package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmConfig {

    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_TIMEOUT = 60000;

    private String provider = "dashscope";
    
    @JsonProperty("api_key")
    private String apiKey;
    
    private String model;
    
    @JsonProperty("base_url")
    private String baseUrl;
    
    @JsonProperty("max_tokens")
    private int maxTokens = DEFAULT_MAX_TOKENS;
    
    private double temperature = DEFAULT_TEMPERATURE;
    private int timeout = DEFAULT_TIMEOUT;

    public LlmConfig() {
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2");
        }
        this.temperature = temperature;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than 0");
        }
        this.timeout = timeout;
    }

    public boolean isValid() {
        if (isLocalProvider()) {
            return true;
        }
        return apiKey != null 
            && !apiKey.isEmpty() 
            && !apiKey.equals("your-api-key-here");
    }

    @JsonIgnore
    public boolean isLocalProvider() {
        if (provider == null) {
            return false;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("ollama") || normalized.equals("local");
    }

    public String maskApiKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public String toString() {
        return "LlmConfig{" +
                "provider='" + provider + '\'' +
                ", apiKey='" + maskApiKey() + '\'' +
                ", model='" + model + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                ", timeout=" + timeout +
                '}';
    }
}
