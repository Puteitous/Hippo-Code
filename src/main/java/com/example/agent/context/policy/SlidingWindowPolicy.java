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
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        this.tokenEstimator = tokenEstimator;
        if (config != null) {
            this.keepRecentTurns = config.getKeepRecentTurns();
        } else {
            this.keepRecentTurns = ContextConfig.DEFAULT_KEEP_RECENT_TURNS;
        }
    }

    public SlidingWindowPolicy(TokenEstimator tokenEstimator, int keepRecentTurns) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        this.tokenEstimator = tokenEstimator;
        this.keepRecentTurns = keepRecentTurns > 0 ? keepRecentTurns : ContextConfig.DEFAULT_KEEP_RECENT_TURNS;
    }

    @Override
    public List<Message> apply(List<Message> messages, int maxTokens, int maxMessages) {
        if (messages == null) {
            return null;
        }
        if (messages.size() <= 2) {
            return new ArrayList<>(messages);
        }

        Message systemMessage = messages.get(0);
        List<Message> conversationMessages = new ArrayList<>(messages.subList(1, messages.size()));

        List<Message> windowMessages = extractRecentTurns(conversationMessages);

        List<Message> result = new ArrayList<>();
        result.add(systemMessage);
        result.addAll(windowMessages);

        while (result.size() > 1) {
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
