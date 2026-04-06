package com.example.agent.service;

import com.example.agent.context.Compressor;
import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.compressor.TruncateCompressor;
import com.example.agent.context.policy.SlidingWindowPolicy;
import com.example.agent.llm.model.Message;

import java.util.ArrayList;
import java.util.List;

public class ConversationManager {

    private final List<Message> conversationHistory;
    private final TokenEstimator tokenEstimator;
    private final String systemPrompt;
    
    private final TrimPolicy trimPolicy;
    private final Compressor toolResultCompressor;
    private final ContextConfig config;

    public ConversationManager(String systemPrompt, TokenEstimator tokenEstimator) {
        this(systemPrompt, tokenEstimator, new ContextConfig());
    }

    public ConversationManager(String systemPrompt, TokenEstimator tokenEstimator, ContextConfig config) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig();
        this.conversationHistory = new ArrayList<>();
        
        this.trimPolicy = new SlidingWindowPolicy(tokenEstimator, this.config);
        this.toolResultCompressor = new TruncateCompressor(tokenEstimator, this.config.getToolResult());
        
        reset();
    }

    public ConversationManager(String systemPrompt, TokenEstimator tokenEstimator, 
                               TrimPolicy trimPolicy, Compressor toolResultCompressor, ContextConfig config) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        if (trimPolicy == null) {
            throw new IllegalArgumentException("trimPolicy不能为null");
        }
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig();
        this.conversationHistory = new ArrayList<>();
        this.trimPolicy = trimPolicy;
        this.toolResultCompressor = toolResultCompressor;
        
        reset();
    }

    public void reset() {
        conversationHistory.clear();
        conversationHistory.add(Message.system(systemPrompt));
    }

    public void addUserMessage(String content) {
        conversationHistory.add(Message.user(content));
    }

    public void addAssistantMessage(Message message) {
        if (message == null) {
            return;
        }
        conversationHistory.add(message);
    }

    public void addToolResult(String toolCallId, String toolName, String result) {
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            return;
        }
        if (toolName == null || toolName.trim().isEmpty()) {
            return;
        }
        
        Message toolMessage = Message.toolResult(toolCallId, toolName, result);
        
        if (toolResultCompressor != null && toolResultCompressor.supports(toolMessage)) {
            int maxTokens = config.getToolResult().getMaxTokens();
            toolMessage = toolResultCompressor.compress(toolMessage, maxTokens);
        }
        
        conversationHistory.add(toolMessage);
    }

    public List<Message> getHistory() {
        return conversationHistory;
    }

    public int getMessageCount() {
        return conversationHistory.size();
    }

    public int getTokenCount() {
        return tokenEstimator.estimateConversationTokens(conversationHistory);
    }

    public void trimHistory(TrimCallback callback) {
        int beforeCount = conversationHistory.size();
        int beforeTokens = tokenEstimator.estimateConversationTokens(conversationHistory);
        
        List<Message> trimmed = trimPolicy.apply(
            conversationHistory, 
            config.getMaxTokens(), 
            config.getMaxMessages()
        );
        
        if (trimmed == null) {
            return;
        }
        
        conversationHistory.clear();
        conversationHistory.addAll(trimmed);
        
        if (callback != null && conversationHistory.size() < beforeCount) {
            int currentTokens = tokenEstimator.estimateConversationTokens(conversationHistory);
            callback.onTrimmed(conversationHistory.size(), currentTokens);
        }
    }

    public ContextConfig getConfig() {
        return config;
    }

    public TrimPolicy getTrimPolicy() {
        return trimPolicy;
    }

    public Compressor getToolResultCompressor() {
        return toolResultCompressor;
    }

    @FunctionalInterface
    public interface TrimCallback {
        void onTrimmed(int messageCount, int tokenCount);
    }
}
