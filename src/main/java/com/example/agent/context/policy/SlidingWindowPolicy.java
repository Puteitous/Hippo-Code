package com.example.agent.context.policy;

import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.ArrayList;
import java.util.List;

public class SlidingWindowPolicy implements TrimPolicy {

    private final TokenEstimator tokenEstimator;
    private final int keepRecentTurns;

    public SlidingWindowPolicy(TokenEstimator tokenEstimator, ContextConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.keepRecentTurns = config.getKeepRecentTurns();
    }

    public SlidingWindowPolicy(TokenEstimator tokenEstimator, int keepRecentTurns) {
        this.tokenEstimator = tokenEstimator;
        this.keepRecentTurns = keepRecentTurns;
    }

    @Override
    public List<Message> apply(List<Message> messages, int maxTokens, int maxMessages) {
        if (messages.size() <= 2) {
            return new ArrayList<>(messages);
        }

        Message systemMessage = messages.get(0);
        List<Message> conversationMessages = new ArrayList<>(messages.subList(1, messages.size()));

        List<Message> windowMessages = extractRecentTurns(conversationMessages);

        List<Message> result = new ArrayList<>();
        result.add(systemMessage);
        result.addAll(windowMessages);

        while (result.size() > 2) {
            int totalTokens = tokenEstimator.estimateConversationTokens(result);
            
            if (totalTokens <= maxTokens && result.size() <= maxMessages) {
                break;
            }

            result.remove(1);
        }

        return result;
    }

    private List<Message> extractRecentTurns(List<Message> messages) {
        if (messages.size() <= keepRecentTurns * 2) {
            return new ArrayList<>(messages);
        }

        int turnCount = 0;
        int startIndex = messages.size();

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                turnCount++;
                if (turnCount >= keepRecentTurns) {
                    startIndex = i;
                    break;
                }
            }
        }

        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    public int getKeepRecentTurns() {
        return keepRecentTurns;
    }
}
