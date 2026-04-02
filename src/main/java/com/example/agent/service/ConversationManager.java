package com.example.agent.service;

import com.example.agent.llm.Message;

import java.util.ArrayList;
import java.util.List;

public class ConversationManager {

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_CONTEXT_TOKENS = 30000;

    private final List<Message> conversationHistory;
    private final TokenEstimator tokenEstimator;
    private final String systemPrompt;

    public ConversationManager(String systemPrompt, TokenEstimator tokenEstimator) {
        this.systemPrompt = systemPrompt;
        this.tokenEstimator = tokenEstimator;
        this.conversationHistory = new ArrayList<>();
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
        conversationHistory.add(message);
    }

    public void addToolResult(String toolCallId, String toolName, String result) {
        conversationHistory.add(Message.toolResult(toolCallId, toolName, result));
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
        boolean trimmed = false;
        
        while (conversationHistory.size() > 2) {
            int totalTokens = tokenEstimator.estimateConversationTokens(conversationHistory);
            
            if (totalTokens <= MAX_CONTEXT_TOKENS && conversationHistory.size() <= MAX_HISTORY_MESSAGES + 1) {
                break;
            }
            
            conversationHistory.remove(1);
            trimmed = true;
        }
        
        if (trimmed && callback != null) {
            int currentTokens = tokenEstimator.estimateConversationTokens(conversationHistory);
            callback.onTrimmed(conversationHistory.size(), currentTokens);
        }
    }

    @FunctionalInterface
    public interface TrimCallback {
        void onTrimmed(int messageCount, int tokenCount);
    }
}