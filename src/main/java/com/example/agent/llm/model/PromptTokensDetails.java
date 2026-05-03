package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Prompt Token 详情（DashScope/OpenAI 缓存指标）
 * 
 * 对应 API 返回的 usage.prompt_tokens_details 对象：
 * - cached_tokens: 命中缓存的 Token 数
 * - cache_creation_input_tokens: 创建缓存的 Token 数
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptTokensDetails {
    
    @JsonProperty("cached_tokens")
    private int cachedTokens;
    
    @JsonProperty("cache_creation_input_tokens")
    private int cacheCreationInputTokens;
    
    public int getCachedTokens() {
        return cachedTokens;
    }
    
    public void setCachedTokens(int cachedTokens) {
        this.cachedTokens = cachedTokens;
    }
    
    public int getCacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }
    
    public void setCacheCreationInputTokens(int cacheCreationInputTokens) {
        this.cacheCreationInputTokens = cacheCreationInputTokens;
    }
}
