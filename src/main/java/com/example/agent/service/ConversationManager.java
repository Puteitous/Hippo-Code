package com.example.agent.service;

import com.example.agent.context.Compressor;
import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.compressor.TruncateCompressor;
import com.example.agent.context.policy.SlidingWindowPolicy;
import com.example.agent.llm.model.Message;
import com.example.agent.session.SessionData;

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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public SessionData exportSession(String sessionId, SessionData.Status status) {
        List<Message> messagesCopy = new ArrayList<>(conversationHistory);
        return SessionData.create(sessionId, messagesCopy, status);
    }

    public void importSession(SessionData session) {
        if (session == null || session.getMessages() == null) {
            return;
        }
        
        conversationHistory.clear();
        conversationHistory.addAll(session.getMessages());
    }

    public boolean hasUnfinishedToolCall() {
        if (conversationHistory.isEmpty()) {
            return false;
        }
        
        Message lastMessage = conversationHistory.get(conversationHistory.size() - 1);
        if ("assistant".equals(lastMessage.getRole()) && 
            lastMessage.getToolCalls() != null && 
            !lastMessage.getToolCalls().isEmpty()) {
            return true;
        }
        return false;
    }

    public void fixUnfinishedToolCall() {
        if (!hasUnfinishedToolCall()) {
            return;
        }
        
        Message lastMessage = conversationHistory.get(conversationHistory.size() - 1);
        conversationHistory.remove(conversationHistory.size() - 1);
        
        String content = lastMessage.getContent();
        if (content == null || content.trim().isEmpty()) {
            conversationHistory.add(Message.assistant("（会话中断，请继续）"));
        } else {
            conversationHistory.add(Message.assistant(content + "\n\n（会话中断，请继续）"));
        }
    }

    @FunctionalInterface
    public interface TrimCallback {
        void onTrimmed(int messageCount, int tokenCount);
    }
}
