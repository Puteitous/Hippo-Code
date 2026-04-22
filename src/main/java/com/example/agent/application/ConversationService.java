package com.example.agent.application;

import com.example.agent.context.BudgetWarningInjector;
import com.example.agent.context.Compressor;
import com.example.agent.context.SessionCompactionState;
import com.example.agent.context.compressor.AutoCompactTrigger;
import com.example.agent.context.compressor.TruncateCompressor;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.memory.BackgroundExtractor;
import com.example.agent.memory.MemoryRetriever;
import com.example.agent.memory.MemoryStore;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionTranscript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;
    private final ContextConfig defaultConfig;
    private final Compressor toolResultCompressor;
    private final MemoryStore globalMemoryStore;

    private final Map<String, ConversationComponents> componentRegistry = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastAccessTime = new ConcurrentHashMap<>();

    private Consumer<Message> messageListener;
    private Consumer<Message> messageSyncListener;

    private static class ConversationComponents {
        final BudgetWarningInjector warningInjector;
        final AutoCompactTrigger autoCompactTrigger;
        final MemoryRetriever memoryRetriever;
        final BackgroundExtractor backgroundExtractor;
        final SessionTranscript transcript;
        final SessionCompactionState compactionState;

        ConversationComponents(BudgetWarningInjector warningInjector,
                                AutoCompactTrigger autoCompactTrigger,
                                MemoryRetriever memoryRetriever,
                                BackgroundExtractor backgroundExtractor,
                                SessionTranscript transcript,
                                SessionCompactionState compactionState) {
            this.warningInjector = warningInjector;
            this.autoCompactTrigger = autoCompactTrigger;
            this.memoryRetriever = memoryRetriever;
            this.backgroundExtractor = backgroundExtractor;
            this.transcript = transcript;
            this.compactionState = compactionState;
        }
    }

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
        this.globalMemoryStore = new MemoryStore(llmClient);
    }

    public Conversation create(String systemPrompt) {
        return create(systemPrompt, defaultConfig.getMaxTokens());
    }

    public Conversation create(String systemPrompt, int maxTokens) {
        String sessionId = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(maxTokens, tokenEstimator, sessionId);
        conversation.setSystemPrompt(systemPrompt != null ? systemPrompt : "");

        initializeComponents(conversation);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            conversation.addMessage(Message.system(systemPrompt));
        }

        logger.debug("创建新会话: sessionId={}, systemPrompt长度={}", 
            sessionId, systemPrompt != null ? systemPrompt.length() : 0);
        return conversation;
    }

    private void initializeComponents(Conversation conversation) {
        String sessionId = conversation.getSessionId();
        
        SessionTranscript transcript = new SessionTranscript(sessionId);
        BudgetWarningInjector warningInjector = new BudgetWarningInjector(conversation.getContextWindow());
        warningInjector.register();

        SessionCompactionState compactionState = new SessionCompactionState();
        AutoCompactTrigger autoCompactTrigger = new AutoCompactTrigger(
            conversation.getContextWindow(),
            tokenEstimator,
            llmClient,
            sessionId,
            transcript,
            compactionState
        );
        autoCompactTrigger.register();

        MemoryRetriever memoryRetriever = new MemoryRetriever(globalMemoryStore);

        BackgroundExtractor backgroundExtractor = new BackgroundExtractor(
            sessionId,
            tokenEstimator,
            llmClient,
            compactionState
        );

        componentRegistry.put(sessionId, new ConversationComponents(
            warningInjector,
            autoCompactTrigger,
            memoryRetriever,
            backgroundExtractor,
            transcript,
            compactionState
        ));
        
        sessionLastAccessTime.put(sessionId, System.currentTimeMillis());
    }

    private ConversationComponents getComponents(Conversation conversation) {
        if (conversation == null) {
            logger.warn("conversation 为 null");
            return null;
        }
        String sessionId = conversation.getSessionId();
        if (sessionId == null) {
            logger.warn("sessionId 为 null");
            return null;
        }
        sessionLastAccessTime.put(sessionId, System.currentTimeMillis());
        return componentRegistry.get(sessionId);
    }

    public void destroy(Conversation conversation) {
        String sessionId = conversation.getSessionId();
        componentRegistry.remove(sessionId);
        sessionLastAccessTime.remove(sessionId);
        conversation.clear();
        logger.debug("销毁会话: sessionId={}", sessionId);
    }

    public void cleanupIdleSessions(long idleTimeoutMs) {
        long now = System.currentTimeMillis();
        int cleanedCount = 0;
        
        Iterator<Map.Entry<String, Long>> iterator = sessionLastAccessTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > idleTimeoutMs) {
                String sessionId = entry.getKey();
                componentRegistry.remove(sessionId);
                iterator.remove();
                cleanedCount++;
                logger.debug("清理空闲会话: sessionId={}", sessionId);
            }
        }
        
        if (cleanedCount > 0) {
            logger.info("空闲会话清理完成: 清理 {} 个，剩余活跃会话 {}", 
                cleanedCount, componentRegistry.size());
        }
    }

    public int getActiveSessionCount() {
        return componentRegistry.size();
    }

    public void reset(Conversation conversation) {
        if (conversation == null) {
            logger.warn("尝试重置 null 的 conversation");
            return;
        }
        String sessionId = conversation.getSessionId();
        conversation.clear();
        componentRegistry.remove(sessionId);
        sessionLastAccessTime.remove(sessionId);
        initializeComponents(conversation);
        
        String systemPrompt = conversation.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            conversation.addMessage(Message.system(systemPrompt));
        }
    }

    public void setSystemPrompt(Conversation conversation, String newSystemPrompt) {
        setSystemPrompt(conversation, newSystemPrompt, false);
    }

    public void setSystemPrompt(Conversation conversation, String newSystemPrompt, boolean preserveHistory) {
        conversation.setSystemPrompt(newSystemPrompt != null ? newSystemPrompt : "");
        
        if (!preserveHistory) {
            reset(conversation);
        }
    }

    public void addUserMessage(Conversation conversation, String content) {
        addMessage(conversation, Message.user(content));
    }

    public void addAssistantMessage(Conversation conversation, String content) {
        addMessage(conversation, Message.assistant(content));
    }

    public void addAssistantMessage(Conversation conversation, Message message, Usage usage) {
        addMessage(conversation, message);
    }

    public void addToolResult(Conversation conversation, String toolCallId, String toolName, String content) {
        String compressed = toolResultCompressor.compress(content);
        addMessage(conversation, Message.toolResult(toolCallId, toolName, compressed));
    }

    public void addMessage(Conversation conversation, Message message) {
        if (conversation == null) {
            logger.warn("conversation 为 null，跳过添加消息");
            return;
        }
        if (message == null) {
            logger.warn("message 为 null，跳过添加");
            return;
        }
        
        ConversationComponents components = getComponents(conversation);
        
        conversation.addMessage(message);
        notifyMessageAdded(message);

        if (components != null) {
            components.backgroundExtractor.onMessageAdded(message, conversation.getMessages());
            
            if (message.getContent() != null && conversation.shouldMarkForMemory(message)) {
                components.memoryRetriever.markForMemory(message.getContent());
            }
            
            if (message.isUser()) {
                components.transcript.appendUserMessage(message);
            } else if (message.isAssistant()) {
                components.transcript.appendAssistantMessage(message, null);
            } else if (message.isTool()) {
                components.transcript.appendToolResult(message, message.getName(), 0, true);
            } else if (message.isSystem()) {
                components.transcript.appendSystemMessage(message.getContent());
            }
        }
    }

    public List<Message> prepareForInference(Conversation conversation) {
        ConversationComponents components = getComponents(conversation);
        
        if (components != null) {
            components.autoCompactTrigger.startNewQueryLoop();
            components.autoCompactTrigger.ensureResumeWindowIfNeeded();
        }

        List<Message> effectiveMessages = conversation.getEffectiveMessages();
        
        if (components != null) {
            effectiveMessages = components.memoryRetriever.prepareContextHeader(effectiveMessages);
        }
        
        return effectiveMessages;
    }

    public String getCompactionStats(Conversation conversation) {
        ConversationComponents components = getComponents(conversation);
        return components != null 
            ? components.autoCompactTrigger.getMetrics().getSummary() 
            : "No compaction data available";
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
            return true;
        } catch (Exception e) {
            logger.error("导入会话失败", e);
            return false;
        }
    }

    public List<Message> getContextForInference(Conversation conversation) {
        return prepareForInference(conversation);
    }

    public void cleanupInterruptedToolCalls(Conversation conversation) {
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

    public int getMessageCount(Conversation conversation) {
        return conversation.getMessageCount();
    }

    public List<Message> getHistory(Conversation conversation) {
        return conversation.getMessages();
    }

    public void fixUnfinishedToolCall(Conversation conversation) {
        cleanupInterruptedToolCalls(conversation);
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
