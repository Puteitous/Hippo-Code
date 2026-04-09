package com.example.agent.context.policy;

import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;

import java.util.List;

/**
 * 上下文策略接口
 * 定义如何构建和管理对话上下文
 */
public interface ContextPolicy {

    /**
     * 构建上下文
     *
     * @param userInput 用户输入
     * @param baseManager 基础对话管理器
     * @param maxTokens 最大token限制
     * @return 构建好的消息列表
     */
    List<Message> buildContext(String userInput, ConversationManager baseManager, int maxTokens);

    /**
     * 策略名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 是否支持该策略
     */
    default boolean isSupported() {
        return true;
    }
}
