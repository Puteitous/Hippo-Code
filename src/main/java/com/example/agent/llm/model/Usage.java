package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Usage {

    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    
    // DashScope/OpenAI 缓存指标（嵌套在 prompt_tokens_details 中）
    @JsonProperty("prompt_tokens_details")
    private PromptTokensDetails promptTokensDetails;

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    public PromptTokensDetails getPromptTokensDetails() {
        return promptTokensDetails;
    }
    
    public void setPromptTokensDetails(PromptTokensDetails promptTokensDetails) {
        this.promptTokensDetails = promptTokensDetails;
    }
    
    /**
     * 获取命中缓存的 Token 数
     */
    public int getCacheReadInputTokens() {
        return promptTokensDetails != null ? promptTokensDetails.getCachedTokens() : 0;
    }
    
    /**
     * 获取创建缓存的 Token 数
     */
    public int getCacheCreationInputTokens() {
        return promptTokensDetails != null ? promptTokensDetails.getCacheCreationInputTokens() : 0;
    }
    
    /**
     * 计算缓存命中率
     * @return 缓存命中率百分比 (0-100)
     */
    public double getCacheHitRate() {
        int cacheRead = getCacheReadInputTokens();
        int cacheCreation = getCacheCreationInputTokens();
        int totalInput = promptTokens + cacheRead + cacheCreation;
        if (totalInput == 0) {
            return 0.0;
        }
        return ((double) cacheRead / totalInput) * 100;
    }

    @Override
    public String toString() {
        return "Usage{" +
                "promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens +
                ", cacheReadInputTokens=" + getCacheReadInputTokens() +
                ", cacheCreationInputTokens=" + getCacheCreationInputTokens() +
                ", cacheHitRate=" + String.format("%.1f", getCacheHitRate()) + "%" +
                '}';
    }
}
