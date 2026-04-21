package com.example.agent.service;

import com.example.agent.context.Compressor;
import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.compressor.TruncateCompressor;
import com.example.agent.context.policy.SlidingWindowPolicy;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionTranscript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConversationManager {

    private static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    private final List<Message> conversationHistory;
    private final TokenEstimator tokenEstimator;
    private String systemPrompt;
    
    private TrimPolicy trimPolicy;
    private final Compressor toolResultCompressor;
    private final ContextConfig config;
    
    private SessionTranscript transcript;
    private Consumer<Message> messageListener;

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

    public void setSystemPrompt(String newSystemPrompt) {
        setSystemPrompt(newSystemPrompt, false);
    }

    public void setSystemPrompt(String newSystemPrompt, boolean preserveHistory) {
        this.systemPrompt = newSystemPrompt;

        if (!preserveHistory || conversationHistory.isEmpty()) {
            reset();
            return;
        }

        // ✅ 无缝切换：只替换第一条 System 消息，保留所有对话历史
        if (!conversationHistory.isEmpty() && conversationHistory.get(0).isSystem()) {
            conversationHistory.set(0, Message.system(newSystemPrompt));
        } else {
            conversationHistory.add(0, Message.system(newSystemPrompt));
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void addUserMessage(String content) {
        Message message = Message.user(content);
        conversationHistory.add(message);
        notifyMessageAdded(message);
        
        if (transcript != null) {
            transcript.appendUserMessage(message);
        }
    }

    public void addAssistantMessage(Message message) {
        addAssistantMessage(message, null);
    }

    public void addAssistantMessage(Message message, Usage usage) {
        if (message == null) {
            return;
        }
        conversationHistory.add(message);
        notifyMessageAdded(message);
        
        if (transcript != null) {
            transcript.appendAssistantMessage(message, usage);
        }
    }

    public void addToolResult(String toolCallId, String toolName, String result) {
        addToolResult(toolCallId, toolName, result, 0, true);
    }

    public void addToolResult(String toolCallId, String toolName, String result, long durationMs, boolean success) {
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
        notifyMessageAdded(toolMessage);
        
        if (transcript != null) {
            transcript.appendToolResult(toolMessage, toolName, durationMs, success);
        }
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

    public void setTrimPolicy(TrimPolicy trimPolicy) {
        if (trimPolicy != null) {
            this.trimPolicy = trimPolicy;
        }
    }

    public Compressor getToolResultCompressor() {
        return toolResultCompressor;
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

    public void enableTranscript(String sessionId) {
        if (transcript != null) {
            transcript.close();
        }
        transcript = new SessionTranscript(sessionId);
        logger.debug("Transcript 已启用: {}", sessionId);
    }

    public SessionTranscript getTranscript() {
        return transcript;
    }

    public void disableTranscript() {
        if (transcript != null) {
            transcript.close();
            transcript = null;
        }
    }

    public void setMessageListener(Consumer<Message> listener) {
        this.messageListener = listener;
    }

    private void notifyMessageAdded(Message message) {
        if (messageListener != null) {
            try {
                messageListener.accept(message);
            } catch (Exception e) {
                logger.warn("消息监听器执行失败: {}", e.getMessage());
            }
        }
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
        
        StringBuilder prompt = new StringBuilder();
        
        String content = lastMessage.getContent();
        if (content != null && !content.trim().isEmpty()) {
            prompt.append(content).append("\n\n");
        }
        
        prompt.append("（会话中断");
        
        if (lastMessage.getToolCalls() != null && !lastMessage.getToolCalls().isEmpty()) {
            String toolNames = lastMessage.getToolCalls().stream()
                .filter(tc -> tc != null && tc.getFunction() != null)
                .map(tc -> tc.getFunction().getName())
                .filter(name -> name != null && !name.isEmpty())
                .collect(java.util.stream.Collectors.joining(", "));
            
            if (!toolNames.isEmpty()) {
                prompt.append("，待执行的操作: ").append(toolNames);
            }
        }
        
        prompt.append("，请继续）");
        
        conversationHistory.add(Message.assistant(prompt.toString()));
    }

    @FunctionalInterface
    public interface TrimCallback {
        void onTrimmed(int messageCount, int tokenCount);
    }
}
