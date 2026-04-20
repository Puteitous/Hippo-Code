package com.example.agent.memory;

import com.example.agent.llm.model.Message;
import com.example.agent.domain.rule.HippoRulesParser;

import java.util.ArrayList;
import java.util.List;

public class MemoryRetriever {

    private final MemoryStore memoryStore;
    private final HippoRulesParser rulesParser;
    private boolean injectionEnabled = true;

    public MemoryRetriever(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        this.rulesParser = new HippoRulesParser();
    }

    public List<Message> prepareContextHeader(List<Message> rawMessages) {
        List<Message> enhancedContext = new ArrayList<>();

        if (injectionEnabled) {
            String currentContext = extractContextForSearch(rawMessages);

            String rulesPrompt = rulesParser.getRulesAsSystemPrompt();
            if (!rulesPrompt.isEmpty()) {
                enhancedContext.add(Message.system(rulesPrompt));
            }

            String memoryPrompt = memoryStore.getRelevantMemoriesAsPrompt(currentContext);
            if (!memoryPrompt.isEmpty()) {
                enhancedContext.add(Message.system(memoryPrompt));
            }
        }

        enhancedContext.addAll(rawMessages);

        memoryStore.triggerAutoDream();

        return enhancedContext;
    }

    private String extractContextForSearch(List<Message> messages) {
        StringBuilder context = new StringBuilder();

        int count = 0;
        for (int i = messages.size() - 1; i >= 0 && count < 5; i--) {
            Message msg = messages.get(i);
            if (msg.isUser() || msg.isAssistant()) {
                context.insert(0, msg.getContent() + " ");
                count++;
            }
        }

        return context.toString();
    }

    public void markForMemory(String candidate) {
        memoryStore.addPendingMemory(candidate);
    }

    public void setInjectionEnabled(boolean enabled) {
        this.injectionEnabled = enabled;
    }

    public MemoryStore getMemoryStore() {
        return memoryStore;
    }

    public HippoRulesParser getRulesParser() {
        return rulesParser;
    }
}
