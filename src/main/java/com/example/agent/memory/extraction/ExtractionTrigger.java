package com.example.agent.memory.extraction;

import com.example.agent.llm.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 记忆提取触发器
 * 
 * 职责：
 * 1. 检查是否应该触发记忆提取
 * 2. 检测主 Agent 是否已直接写记忆（避免重复提取）
 * 3. 维护提取轮次计数
 * 
 * 触发条件：
 * - 主 Agent 未直接写记忆
 * - 达到触发轮次（默认每 3 轮完整对话）
 * 
 * 轮次定义：一轮 = 用户消息 + AI 回复（一个完整的对话回合）
 */
public class ExtractionTrigger {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionTrigger.class);
    
    // 默认每 N 轮完整对话触发一次提取
    private static final int DEFAULT_EXTRACTION_INTERVAL = 3;
    
    private int extractionInterval;
    private int completedRoundsSinceLastExtraction = 0;
    private boolean expectingAssistantResponse = false;
    private String lastMemoryMessageUuid;
    
    // 游标推进回调
    private Runnable onCursorAdvanceCallback;

    public ExtractionTrigger() {
        this(DEFAULT_EXTRACTION_INTERVAL);
    }

    public ExtractionTrigger(int extractionInterval) {
        this.extractionInterval = extractionInterval;
    }

    /**
     * 检查是否应该触发提取
     * 
     * @param conversation 当前对话历史
     * @return true 如果应该触发提取
     */
    public boolean shouldExtract(List<Message> conversation) {
        // 1. 检查主 Agent 是否已直接写记忆
        if (hasMemoryWritesSince(conversation)) {
            logger.debug("跳过提取：主 Agent 已直接写记忆");
            resetRoundCounter();
            return false;
        }

        // 2. 计算完整的对话轮次
        int completedRounds = countCompletedRounds(conversation);
        
        // 3. 检查是否达到触发轮次
        int newRoundsSinceLastExtraction = completedRounds - completedRoundsSinceLastExtraction;
        if (newRoundsSinceLastExtraction < extractionInterval) {
            logger.debug("跳过提取：未达到触发轮次 ({}/{})", 
                newRoundsSinceLastExtraction, extractionInterval);
            return false;
        }

        logger.debug("触发提取：达到触发轮次 ({})", newRoundsSinceLastExtraction);
        completedRoundsSinceLastExtraction = completedRounds;
        return true;
    }

    /**
     * 计算已完成的对话轮次
     * 一轮 = 用户消息 + AI 回复
     */
    private int countCompletedRounds(List<Message> conversation) {
        int rounds = 0;
        boolean hasUserMessage = false;
        
        for (Message message : conversation) {
            if (message.isUser()) {
                hasUserMessage = true;
            } else if (message.isAssistant() && hasUserMessage) {
                // 完成了一个完整的对话轮次
                rounds++;
                hasUserMessage = false;
            }
        }
        
        return rounds;
    }

    /**
     * 检查主 Agent 是否在对话中直接写了记忆文件
     * 
     * @param conversation 对话历史
     * @return true 如果主 Agent 已直接写记忆
     */
    private boolean hasMemoryWritesSince(List<Message> conversation) {
        if (lastMemoryMessageUuid == null) {
            return false;
        }

        boolean foundBoundary = false;
        for (Message message : conversation) {
            // 找到边界消息
            if (message.getId() != null && message.getId().equals(lastMemoryMessageUuid)) {
                foundBoundary = true;
                continue;
            }

            // 只检查边界之后的消息
            if (!foundBoundary) {
                continue;
            }

            // 检查 Assistant 消息的工具调用
            if (message.isAssistant() && message.getToolCalls() != null) {
                for (var toolCall : message.getToolCalls()) {
                    if (isMemoryWriteTool(toolCall)) {
                        // 互斥检查：当主 Agent 写了记忆，跳过提取并推进游标
                        if (onCursorAdvanceCallback != null) {
                            onCursorAdvanceCallback.run();
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 检查工具调用是否是写记忆操作
     */
    private boolean isMemoryWriteTool(com.example.agent.llm.model.ToolCall toolCall) {
        if (toolCall == null || toolCall.getFunction() == null) {
            return false;
        }

        String toolName = toolCall.getFunction().getName();
        // 检查是否是写文件工具
        return "write_file".equals(toolName) || "edit_file".equals(toolName);
    }

    /**
     * 通知主 Agent 已直接写记忆
     * 
     * @param messageUuid 写记忆时的消息 UUID
     */
    public void notifyMemoryWritten(String messageUuid) {
        this.lastMemoryMessageUuid = messageUuid;
        logger.debug("主 Agent 直接写记忆，更新边界: {}", messageUuid);
    }

    /**
     * 重置轮次计数器
     */
    public void resetRoundCounter() {
        completedRoundsSinceLastExtraction = 0;
    }

    /**
     * 获取当前轮次计数
     */
    public int getCompletedRoundsSinceLastExtraction() {
        return completedRoundsSinceLastExtraction;
    }

    /**
     * 设置提取间隔
     */
    public void setExtractionInterval(int interval) {
        this.extractionInterval = interval;
    }

    /**
     * 设置游标推进回调
     * 当检测到主 Agent 已写记忆时调用，用于推进游标
     */
    public void setOnCursorAdvanceCallback(Runnable callback) {
        this.onCursorAdvanceCallback = callback;
    }
}
