package com.example.agent.context;

import com.example.agent.context.compressor.AutoCompactTrigger;
import com.example.agent.core.AgentContext;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.BackgroundExtractor;
import com.example.agent.memory.MemoryRetriever;
import com.example.agent.memory.MemoryStore;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionTranscript;

import java.util.List;
import java.util.UUID;

public class ContextManager {

    private final ContextWindow contextWindow;
    private final BudgetWarningInjector warningInjector;
    private final AutoCompactTrigger autoCompactTrigger;
    private final BlockingGuard blockingGuard;
    private final MemoryRetriever memoryRetriever;
    private final BackgroundExtractor backgroundExtractor;
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;
    private final String sessionId;
    private final SessionTranscript transcript;

    public ContextManager(int maxTokens, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(maxTokens, tokenEstimator, llmClient, UUID.randomUUID().toString());
    }

    public ContextManager(int maxTokens, TokenEstimator tokenEstimator, LlmClient llmClient, String sessionId) {
        this.tokenEstimator = tokenEstimator;
        this.llmClient = llmClient;
        this.sessionId = sessionId;
        this.contextWindow = new ContextWindow(maxTokens, tokenEstimator);
        this.transcript = new SessionTranscript(sessionId);

        this.warningInjector = new BudgetWarningInjector(contextWindow);
        this.warningInjector.register();

        this.autoCompactTrigger = new AutoCompactTrigger(
            contextWindow, 
            tokenEstimator, 
            llmClient, 
            sessionId,
            transcript
        );
        this.autoCompactTrigger.register();

        this.blockingGuard = new BlockingGuard(contextWindow);
        this.blockingGuard.register();

        MemoryStore memoryStore = new MemoryStore(llmClient);
        this.memoryRetriever = new MemoryRetriever(memoryStore);

        this.backgroundExtractor = new BackgroundExtractor(
            sessionId, 
            tokenEstimator, 
            llmClient, 
            autoCompactTrigger.getState()
        );
    }

    public void addMessage(Message message) {
        if (!blockingGuard.canAddMessage()) {
            throw new IllegalStateException(blockingGuard.getStatusMessage());
        }
        contextWindow.addMessage(message);

        backgroundExtractor.onMessageAdded(message, contextWindow.getRawMessages());

        if (shouldMarkForMemory(message)) {
            memoryRetriever.markForMemory(message.getContent());
        }
    }

    public void addMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (!blockingGuard.canAddMessage()) {
            throw new IllegalStateException(blockingGuard.getStatusMessage());
        }
        contextWindow.addMessages(messages);
        
        List<Message> rawMessages = contextWindow.getRawMessages();
        for (Message msg : messages) {
            backgroundExtractor.onMessageAdded(msg, rawMessages);
            if (shouldMarkForMemory(msg)) {
                memoryRetriever.markForMemory(msg.getContent());
            }
        }
    }

    public List<Message> getContext() {
        autoCompactTrigger.startNewQueryLoop();
        autoCompactTrigger.ensureResumeWindowIfNeeded();

        List<Message> effectiveMessages = contextWindow.getEffectiveMessages();
        return memoryRetriever.prepareContextHeader(effectiveMessages);
    }

    public List<Message> getRawMessages() {
        return contextWindow.getRawMessages();
    }

    public TokenBudget getBudget() {
        return contextWindow.getBudget();
    }

    public double getUsageRatio() {
        return contextWindow.getBudget().getUsageRatio();
    }

    public void clear() {
        contextWindow.clear();
        autoCompactTrigger.fullReset();
        warningInjector.reset();
    }

    public void replaceContext(List<Message> newMessages) {
        contextWindow.clearInjectedWarnings();
        contextWindow.replaceMessages(newMessages);
        autoCompactTrigger.reset();
    }

    public int size() {
        return contextWindow.size();
    }

    public ContextWindow getContextWindow() {
        return contextWindow;
    }

    public MemoryRetriever getMemoryRetriever() {
        return memoryRetriever;
    }

    public BackgroundExtractor getBackgroundExtractor() {
        return backgroundExtractor;
    }

    public String getCompactionStats() {
        return autoCompactTrigger.getMetrics().getSummary();
    }

    public BlockingGuard getBlockingGuard() {
        return blockingGuard;
    }

    public boolean canCallTool() {
        return blockingGuard.canCallTool();
    }

    public String getSessionId() {
        return sessionId;
    }

    private boolean shouldMarkForMemory(Message message) {
        if (message.isTool()) {
            return false;
        }

        String content = message.getContent().toLowerCase();

        return content.contains("我希望") ||
               content.contains("我喜欢") ||
               content.contains("记住") ||
               content.contains("我们决定") ||
               content.contains("总结：") ||
               content.contains("经验：") ||
               content.contains("教训：") ||
               (message.isAssistant() && content.contains("已完成") && content.length() > 200);
    }

    public static void initialize(AgentContext context) {
        int maxTokens = context.getConfig().getMaxTokens();
        String sessionId = context.getSessionId();
        ContextManager instance = new ContextManager(
            maxTokens,
            context.getTokenEstimator(),
            context.getLlmClient(),
            sessionId
        );
        ServiceLocator.registerSingleton(ContextManager.class, instance);
    }
}
