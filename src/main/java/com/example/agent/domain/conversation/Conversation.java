package com.example.agent.domain.conversation;

import com.example.agent.context.BlockingGuard;
import com.example.agent.context.BudgetWarningInjector;
import com.example.agent.context.ContextWindow;
import com.example.agent.context.TokenBudget;
import com.example.agent.context.compressor.AutoCompactTrigger;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.BackgroundExtractor;
import com.example.agent.memory.MemoryRetriever;
import com.example.agent.memory.MemoryStore;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionTranscript;

import java.util.List;
import java.util.UUID;

public class Conversation {

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
    private String systemPrompt;

    public Conversation(int maxTokens, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(maxTokens, tokenEstimator, llmClient, UUID.randomUUID().toString());
    }

    public Conversation(int maxTokens, TokenEstimator tokenEstimator, LlmClient llmClient, String sessionId) {
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

    public List<Message> prepareForInference() {
        autoCompactTrigger.startNewQueryLoop();
        autoCompactTrigger.ensureResumeWindowIfNeeded();

        List<Message> effectiveMessages = contextWindow.getEffectiveMessages();
        return memoryRetriever.prepareContextHeader(effectiveMessages);
    }

    public List<Message> getMessages() {
        return contextWindow.getRawMessages();
    }

    public TokenBudget getBudget() {
        return contextWindow.getBudget();
    }

    public double getUsageRatio() {
        return contextWindow.getBudget().getUsageRatio();
    }

    public int getTokenCount() {
        return contextWindow.getBudget().getCurrentTokens();
    }

    public void clear() {
        contextWindow.clear();
        autoCompactTrigger.fullReset();
        warningInjector.reset();
    }

    public void replaceMessages(List<Message> newMessages) {
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

    public String getSessionId() {
        return sessionId;
    }

    public SessionTranscript getTranscript() {
        return transcript;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public LlmClient getLlmClient() {
        return llmClient;
    }

    public TokenEstimator getTokenEstimator() {
        return tokenEstimator;
    }

    private boolean shouldMarkForMemory(Message message) {
        return message.isUser() && message.getContent() != null 
            && message.getContent().length() > 20;
    }
}
