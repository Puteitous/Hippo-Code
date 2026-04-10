package com.example.agent.context.policy;

import com.example.agent.context.TrimPolicy;
import com.example.agent.context.memory.WarmMemory;
import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 三层记忆策略
 * 整合 HotMemory、WarmMemory、ColdMemory 三层记忆管理
 * 目前作为骨架实现，后续阶段逐步填充各层功能
 */
public class ThreeTierPolicy implements ContextPolicy {

    private static final Logger logger = LoggerFactory.getLogger(ThreeTierPolicy.class);

    private final TrimPolicy trimPolicy;

    // 三层记忆（后续阶段实现）
    // private final HotMemory hotMemory;
    // private final WarmMemory warmMemory;
    // private final ColdMemory coldMemory;

    public ThreeTierPolicy(TrimPolicy trimPolicy) {
        this.trimPolicy = trimPolicy;
    }

    @Override
    public List<Message> buildContext(String userInput, ConversationManager baseManager, int maxTokens) {
        if (baseManager == null) {
            throw new IllegalArgumentException("baseManager不能为null");
        }

        logger.debug("使用 ThreeTierPolicy 构建上下文");

        // 获取基础对话历史
        List<Message> context = new ArrayList<>(baseManager.getHistory());

        // 添加用户输入（如果不在历史中）
        if (userInput != null && !userInput.isEmpty()) {
            boolean alreadyAdded = context.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .anyMatch(m -> userInput.equals(m.getContent()));

            if (!alreadyAdded) {
                context.add(Message.user(userInput));
            }
        }

        // 应用裁剪策略
        if (trimPolicy != null) {
            context = trimPolicy.apply(context, maxTokens, Integer.MAX_VALUE);
        }

        return context;
    }

    @Override
    public String getName() {
        return "ThreeTierPolicy";
    }

}
