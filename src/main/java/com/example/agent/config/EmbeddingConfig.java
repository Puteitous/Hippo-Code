package com.example.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingConfig {

    private static final String DEFAULT_PROVIDER = "local";
    private static final String DEFAULT_MODEL_DIR = ".hippo/models/embedding";
    private static final String DEFAULT_OPENAI_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_OPENAI_DIMENSION = 1536;

    private String provider = DEFAULT_PROVIDER;
    
    @JsonProperty("model_dir")
    private String modelDir = DEFAULT_MODEL_DIR;
    
    @JsonProperty("api_key")
    private String apiKey;
    
    @JsonProperty("base_url")
    private String baseUrl;
    
    private String model = DEFAULT_OPENAI_MODEL;
    private int dimension = DEFAULT_OPENAI_DIMENSION;

    public EmbeddingConfig() {
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelDir() {
        return modelDir;
    }

    public void setModelDir(String modelDir) {
        this.modelDir = modelDir;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public String toString() {
        return "EmbeddingConfig{" +
                "provider='" + provider + '\'' +
                ", modelDir='" + modelDir + '\'' +
                ", apiKey='" + (apiKey != null ? "****" : "null") + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", model='" + model + '\'' +
                ", dimension=" + dimension +
                '}';
    }
}
