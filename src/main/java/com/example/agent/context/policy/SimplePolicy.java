package com.example.agent.context.policy;

import com.example.agent.context.TrimPolicy;
import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单策略
 * 仅使用对话历史，不加载任何额外记忆
 * 作为 baseline 和 fallback 策略
 */
public class SimplePolicy implements ContextPolicy {

    private final TrimPolicy trimPolicy;

    public SimplePolicy(TrimPolicy trimPolicy) {
        this.trimPolicy = trimPolicy;
    }

    @Override
    public List<Message> buildContext(String userInput, ConversationManager baseManager, int maxTokens) {
        if (baseManager == null) {
            throw new IllegalArgumentException("baseManager不能为null");
        }

        // 获取现有对话历史
        List<Message> history = new ArrayList<>(baseManager.getHistory());

        // 添加用户输入（如果不在历史中）
        if (userInput != null && !userInput.isEmpty()) {
            boolean alreadyAdded = history.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .anyMatch(m -> userInput.equals(m.getContent()));

            if (!alreadyAdded) {
                history.add(Message.user(userInput));
            }
        }

        // 应用裁剪策略
        if (trimPolicy != null) {
            return trimPolicy.apply(history, maxTokens, Integer.MAX_VALUE);
        }

        return history;
    }

    @Override
    public String getName() {
        return "SimplePolicy";
    }
}
