package com.example.agent.memory;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.core.event.EventBus;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.consolidation.MemoryConsolidator;
import com.example.agent.memory.session.SessionMemoryExtractor;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 记忆提取与整合协调器（向后兼容包装器）
 * 
 * @deprecated 此类仅为向后兼容而保留。
 * 新代码应直接使用：
 * - {@link SessionMemoryExtractor} 用于会话记忆提取
 * - {@link MemoryConsolidator} 用于后台记忆整合
 * 
 * 职责：
 * 1. 协调会话记忆提取（委托给 SessionMemoryExtractor）
 * 2. 协调后台记忆整合（委托给 MemoryConsolidator）
 * 3. 提供向后兼容的 API
 */
@Deprecated
public class BackgroundExtractor {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundExtractor.class);

    private final SessionMemoryExtractor sessionExtractor;
    private final MemoryConsolidator consolidator;

    /**
     * 构造函数
     *
     * @param sessionId 会话 ID
     * @param tokenEstimator Token 估算器
     * @param llmClient LLM 客户端
     */
    public BackgroundExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(sessionId, tokenEstimator, llmClient, new SessionCompactionState(), null);
    }

    /**
     * 构造函数
     *
     * @param sessionId 会话 ID
     * @param tokenEstimator Token 估算器
     * @param llmClient LLM 客户端
     * @param compactionState 压缩状态
     */
    public BackgroundExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient, 
                               SessionCompactionState compactionState) {
        this(sessionId, tokenEstimator, llmClient, compactionState, null);
    }

    /**
     * 构造函数（完整参数）
     *
     * @param sessionId 会话 ID
     * @param tokenEstimator Token 估算器
     * @param llmClient LLM 客户端
     * @param compactionState 压缩状态
     * @param baseDir 基础目录（可选）
     */
    public BackgroundExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient,
                               SessionCompactionState compactionState, java.nio.file.Path baseDir) {
        // 初始化会话记忆提取器
        this.sessionExtractor = new SessionMemoryExtractor(sessionId, tokenEstimator, llmClient, compactionState, baseDir);
        
        // 初始化后台整合器
        this.consolidator = new MemoryConsolidator(llmClient);
        
        logger.info("BackgroundExtractor 已初始化（委托模式）");
    }

    /**
     * 消息添加回调
     *
     * @param message 新添加的消息
     * @param fullConversation 完整对话历史
     */
    public void onMessageAdded(Message message, List<Message> fullConversation) {
        sessionExtractor.onMessageAdded(message, fullConversation);
    }

    /**
     * 检查并执行提取
     *
     * @param fullConversation 完整对话历史
     */
    public void checkAndExtract(List<Message> fullConversation) {
        sessionExtractor.checkAndExtract(fullConversation);
    }

    /**
     * 压缩后请求提取
     *
     * @param fullConversation 完整对话历史
     */
    public void requestExtractionAfterCompaction(List<Message> fullConversation) {
        sessionExtractor.requestExtractionAfterCompaction(fullConversation);
    }

    /**
     * 注册新会话（用于整合器）
     *
     * @param sessionId 会话 ID
     */
    public void registerSession(String sessionId) {
        consolidator.registerSession(sessionId);
    }

    /**
     * 检查并触发整合
     *
     * @param currentSessionId 当前会话 ID
     */
    public void checkAndConsolidate(String currentSessionId) {
        consolidator.checkAndConsolidate(currentSessionId);
    }

    /**
     * 是否有记忆
     *
     * @return true 如果有记忆
     */
    public boolean hasMemory() {
        return sessionExtractor.hasMemory();
    }

    /**
     * 获取会话记忆管理器
     *
     * @return 会话记忆管理器
     */
    public com.example.agent.memory.session.SessionMemoryManager getMemoryManager() {
        return sessionExtractor.getMemoryManager();
    }

    /**
     * 获取会话提取器
     *
     * @return 会话提取器
     */
    public SessionMemoryExtractor getSessionExtractor() {
        return sessionExtractor;
    }

    /**
     * 获取整合器
     *
     * @return 整合器
     */
    public MemoryConsolidator getConsolidator() {
        return consolidator;
    }
}
