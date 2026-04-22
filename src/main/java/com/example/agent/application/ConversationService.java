package com.example.agent.application;

import com.example.agent.context.Compressor;
import com.example.agent.context.compressor.TruncateCompressor;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionTranscript;
import com.example.agent.session.TranscriptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;
    private final ContextConfig defaultConfig;
    private final Compressor toolResultCompressor;

    private Consumer<Message> messageListener;
    private Consumer<Message> messageSyncListener;

    public ConversationService(TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(tokenEstimator, llmClient, new ContextConfig());
    }

    public ConversationService(TokenEstimator tokenEstimator, LlmClient llmClient, ContextConfig config) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        if (llmClient == null) {
            throw new IllegalArgumentException("llmClient不能为null");
        }
        this.tokenEstimator = tokenEstimator;
        this.llmClient = llmClient;
        this.defaultConfig = config != null ? config : new ContextConfig();
        this.toolResultCompressor = new TruncateCompressor(tokenEstimator, this.defaultConfig.getToolResult());
    }

    public Conversation create(String systemPrompt) {
        return create(systemPrompt, defaultConfig.getMaxTokens());
    }

    public Conversation create(String systemPrompt, int maxTokens) {
        String sessionId = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(maxTokens, tokenEstimator, llmClient, sessionId);
        conversation.setSystemPrompt(systemPrompt != null ? systemPrompt : "");

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            conversation.addMessage(Message.system(systemPrompt));
        }

        logger.debug("创建新会话: sessionId={}, systemPrompt长度={}", 
            sessionId, systemPrompt != null ? systemPrompt.length() : 0);
        return conversation;
    }

    public void reset(Conversation conversation) {
        conversation.clear();
        String systemPrompt = conversation.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            conversation.addMessage(Message.system(systemPrompt));
        }
    }

    public void setSystemPrompt(Conversation conversation, String newSystemPrompt) {
        setSystemPrompt(conversation, newSystemPrompt, false);
    }

    public void setSystemPrompt(Conversation conversation, String newSystemPrompt, boolean preserveHistory) {
        conversation.setSystemPrompt(newSystemPrompt);

        if (!preserveHistory || conversation.size() == 0) {
            reset(conversation);
            return;
        }

        List<Message> messages = new ArrayList<>(conversation.getMessages());
        
        if (!messages.isEmpty() && messages.get(0).isSystem()) {
            messages.set(0, Message.system(newSystemPrompt));
        } else {
            messages.add(0, Message.system(newSystemPrompt));
        }
        
        conversation.replaceMessages(messages);
    }

    public void addUserMessage(Conversation conversation, String content) {
        Message message = Message.user(content);
        conversation.addMessage(message);
        notifyMessageAdded(message);
        
        SessionTranscript transcript = conversation.getTranscript();
        if (transcript != null) {
            transcript.appendUserMessage(message);
        }
    }

    public void addAssistantMessage(Conversation conversation, Message message) {
        addAssistantMessage(conversation, message, null);
    }

    public void addAssistantMessage(Conversation conversation, Message message, Usage usage) {
        if (message == null) {
            return;
        }
        conversation.addMessage(message);
        notifyMessageAdded(message);
        
        SessionTranscript transcript = conversation.getTranscript();
        if (transcript != null) {
            transcript.appendAssistantMessage(message, usage);
        }
    }

    public void addToolResult(Conversation conversation, String toolCallId, String toolName, String result) {
        addToolResult(conversation, toolCallId, toolName, result, 0, true);
    }

    public void addToolResult(Conversation conversation, String toolCallId, String toolName, String result, 
                              long durationMs, boolean success) {
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            return;
        }
        if (toolName == null || toolName.trim().isEmpty()) {
            return;
        }
        
        Message toolMessage = Message.toolResult(toolCallId, toolName, result);
        
        if (toolResultCompressor != null && toolResultCompressor.supports(toolMessage)) {
            int maxTokens = defaultConfig.getToolResult().getMaxTokens();
            toolMessage = toolResultCompressor.compress(toolMessage, maxTokens);
        }
        
        conversation.addMessage(toolMessage);
        notifyMessageAdded(toolMessage);
        
        SessionTranscript transcript = conversation.getTranscript();
        if (transcript != null) {
            transcript.appendToolResult(toolMessage, toolName, durationMs, success);
        }
    }

    public List<Message> getHistory(Conversation conversation) {
        return conversation.getMessages();
    }

    public List<Message> getContextForInference(Conversation conversation) {
        return conversation.prepareForInference();
    }

    public int getMessageCount(Conversation conversation) {
        return conversation.size();
    }

    public int getTokenCount(Conversation conversation) {
        return conversation.getTokenCount();
    }

    public double getTokenUsageRatio(Conversation conversation) {
        return conversation.getUsageRatio();
    }

    public SessionData exportSession(Conversation conversation, String sessionId, SessionData.Status status) {
        return SessionData.create(sessionId, new ArrayList<>(conversation.getMessages()), status);
    }

    public boolean importSession(Conversation conversation, SessionData sessionData) {
        if (sessionData == null || sessionData.getMessages() == null) {
            return false;
        }
        
        try {
            conversation.clear();
            conversation.addMessages(sessionData.getMessages());
            logger.info("导入会话成功: {} 条消息", sessionData.getMessages().size());
            return true;
        } catch (Exception e) {
            logger.error("导入会话失败", e);
            return false;
        }
    }

    public boolean loadFromTranscript(Conversation conversation, String sessionId) {
        return TranscriptLoader.loadToConversation(sessionId, conversation, this);
    }

    public void fixUnfinishedToolCall(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        if (messages.isEmpty()) {
            return;
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (!lastMessage.isAssistant() || lastMessage.getToolCalls() == null || lastMessage.getToolCalls().isEmpty()) {
            return;
        }

        boolean hasToolResult = false;
        for (int i = messages.size() - 2; i >= Math.max(0, messages.size() - 5); i--) {
            Message msg = messages.get(i);
            if (msg.isTool()) {
                hasToolResult = true;
                break;
            }
        }

        if (!hasToolResult) {
            StringBuilder fixContent = new StringBuilder();
            String existingContent = lastMessage.getContent() != null ? lastMessage.getContent() : "";
            if (!existingContent.isEmpty()) {
                fixContent.append(existingContent).append("\n\n");
            }
            fixContent.append("[会话中断] 检测到未完成的工具调用：");
            for (com.example.agent.llm.model.ToolCall call : lastMessage.getToolCalls()) {
                fixContent.append("\n  - 待执行的操作: ").append(call.getFunction().getName());
            }
            fixContent.append("\n\n请继续。");
            lastMessage.setContent(fixContent.toString());
        }
    }

    public void setMessageListener(Consumer<Message> listener) {
        this.messageListener = listener;
    }

    public void setMessageSyncListener(Consumer<Message> listener) {
        this.messageSyncListener = listener;
    }

    public Compressor getToolResultCompressor() {
        return toolResultCompressor;
    }

    public ContextConfig getConfig() {
        return defaultConfig;
    }

    private void notifyMessageAdded(Message message) {
        if (messageListener != null) {
            messageListener.accept(message);
        }
        if (messageSyncListener != null) {
            messageSyncListener.accept(message);
        }
    }
}
