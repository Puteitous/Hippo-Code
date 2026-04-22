package com.example.agent.service;

import com.example.agent.context.Compressor;
import com.example.agent.context.ContextManager;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.compressor.TruncateCompressor;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionTranscript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ConversationManager {

    private static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    private final ContextManager contextManager;
    private final TokenEstimator tokenEstimator;
    private String systemPrompt;
    
    private final Compressor toolResultCompressor;
    private final ContextConfig config;
    
    private SessionTranscript transcript;
    private Consumer<Message> messageListener;
    private Consumer<Message> messageSyncListener;

    public ConversationManager(String systemPrompt, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(systemPrompt, tokenEstimator, llmClient, new ContextConfig());
    }

    public ConversationManager(String systemPrompt, TokenEstimator tokenEstimator, LlmClient llmClient, ContextConfig config) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        if (llmClient == null) {
            throw new IllegalArgumentException("llmClient不能为null");
        }
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig();
        
        String sessionId = UUID.randomUUID().toString();
        this.contextManager = new ContextManager(config.getMaxTokens(), tokenEstimator, llmClient, sessionId);
        
        this.toolResultCompressor = new TruncateCompressor(tokenEstimator, this.config.getToolResult());
        
        reset();
    }

    public void reset() {
        contextManager.clear();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            contextManager.addMessage(Message.system(systemPrompt));
        }
    }

    public void setSystemPrompt(String newSystemPrompt) {
        setSystemPrompt(newSystemPrompt, false);
    }

    public void setSystemPrompt(String newSystemPrompt, boolean preserveHistory) {
        this.systemPrompt = newSystemPrompt;

        if (!preserveHistory || contextManager.size() == 0) {
            reset();
            return;
        }

        List<Message> messages = new ArrayList<>(contextManager.getRawMessages());
        
        if (!messages.isEmpty() && messages.get(0).isSystem()) {
            messages.set(0, Message.system(newSystemPrompt));
        } else {
            messages.add(0, Message.system(newSystemPrompt));
        }
        
        contextManager.replaceContext(messages);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void addUserMessage(String content) {
        Message message = Message.user(content);
        contextManager.addMessage(message);
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
        contextManager.addMessage(message);
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
        
        contextManager.addMessage(toolMessage);
        notifyMessageAdded(toolMessage);
        
        if (transcript != null) {
            transcript.appendToolResult(toolMessage, toolName, durationMs, success);
        }
    }

    public List<Message> getHistory() {
        return contextManager.getRawMessages();
    }

    public List<Message> getContextForInference() {
        return contextManager.getContext();
    }

    public int getMessageCount() {
        return contextManager.size();
    }

    public int getTokenCount() {
        return contextManager.getBudget().getCurrentTokens();
    }

    public double getTokenUsageRatio() {
        return contextManager.getUsageRatio();
    }

    public ContextConfig getConfig() {
        return config;
    }

    public Compressor getToolResultCompressor() {
        return toolResultCompressor;
    }

    public ContextManager getContextManager() {
        return contextManager;
    }

    public SessionData exportSession(String sessionId, SessionData.Status status) {
        List<Message> messagesCopy = new ArrayList<>(contextManager.getRawMessages());
        return SessionData.create(sessionId, messagesCopy, status);
    }

    public void importSession(SessionData session) {
        if (session == null || session.getMessages() == null) {
            return;
        }
        contextManager.replaceContext(session.getMessages());
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

    public void fixUnfinishedToolCall() {
        List<Message> messages = contextManager.getRawMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.isAssistant() && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                logger.debug("检测到未完成的工具调用，已清理");
                break;
            }
        }
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

    public void setMessageSyncListener(Consumer<Message> listener) {
        this.messageSyncListener = listener;
    }

    private void notifyMessageAdded(Message message) {
        if (messageListener != null) {
            try {
                messageListener.accept(message);
            } catch (Exception e) {
                logger.warn("消息监听器回调异常", e);
            }
        }
        if (messageSyncListener != null) {
            try {
                messageSyncListener.accept(message);
            } catch (Exception e) {
                logger.warn("同步消息监听器回调异常", e);
            }
        }
    }
}
