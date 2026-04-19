package com.example.agent.context;

import com.example.agent.context.compressor.AutoCompactTrigger;
import com.example.agent.context.compressor.SlidingWindowTrigger;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.MemoryRetriever;
import com.example.agent.memory.MemoryStore;
import com.example.agent.service.TokenEstimator;

import java.util.List;

public class ContextManager {

    private final ContextWindow contextWindow;
    private final BudgetWarningInjector warningInjector;
    private final SlidingWindowTrigger slidingWindowTrigger;
    private final AutoCompactTrigger autoCompactTrigger;
    private final BlockingGuard blockingGuard;
    private final MemoryRetriever memoryRetriever;
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;

    public ContextManager(int maxTokens, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this.tokenEstimator = tokenEstimator;
        this.llmClient = llmClient;
        this.contextWindow = new ContextWindow(maxTokens, tokenEstimator);

        this.warningInjector = new BudgetWarningInjector(contextWindow);
        this.warningInjector.register();

        this.slidingWindowTrigger = new SlidingWindowTrigger(contextWindow, tokenEstimator);
        this.slidingWindowTrigger.register();

        this.autoCompactTrigger = new AutoCompactTrigger(contextWindow, tokenEstimator, llmClient);
        this.autoCompactTrigger.register();

        this.blockingGuard = new BlockingGuard(contextWindow);
        this.blockingGuard.register();

        MemoryStore memoryStore = new MemoryStore(llmClient);
        this.memoryRetriever = new MemoryRetriever(memoryStore);
    }

    public void addMessage(Message message) {
        if (!blockingGuard.canAddMessage()) {
            throw new IllegalStateException(blockingGuard.getStatusMessage());
        }
        contextWindow.addMessage(message);

        if (shouldMarkForMemory(message)) {
            memoryRetriever.markForMemory(message.getContent());
        }
    }

    public void addMessages(List<Message> messages) {
        for (Message msg : messages) {
            addMessage(msg);
        }
    }

    public List<Message> getContext() {
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
        slidingWindowTrigger.reset();
        autoCompactTrigger.fullReset();
        warningInjector.reset();
    }

    public void replaceContext(List<Message> newMessages) {
        contextWindow.clearInjectedWarnings();
        contextWindow.replaceMessages(newMessages);
        slidingWindowTrigger.reset();
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

    public BlockingGuard getBlockingGuard() {
        return blockingGuard;
    }

    public boolean canCallTool() {
        return blockingGuard.canCallTool();
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
}
