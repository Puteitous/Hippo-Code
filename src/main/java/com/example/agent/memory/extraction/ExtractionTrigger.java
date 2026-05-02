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
 * - 达到触发轮次（默认每 N 轮）
 */
public class ExtractionTrigger {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionTrigger.class);
    
    // 默认每 N 轮触发一次提取
    private static final int DEFAULT_EXTRACTION_INTERVAL = 1;
    
    private int extractionInterval;
    private int turnsSinceLastExtraction = 0;
    private String lastMemoryMessageUuid;

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
            resetTurnsCounter();
            return false;
        }

        // 2. 检查触发轮次
        turnsSinceLastExtraction++;
        if (turnsSinceLastExtraction < extractionInterval) {
            logger.debug("跳过提取：未达到触发轮次 ({}/{})", turnsSinceLastExtraction, extractionInterval);
            return false;
        }

        logger.debug("触发提取：达到触发轮次 ({})", turnsSinceLastExtraction);
        resetTurnsCounter();
        return true;
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
    public void resetTurnsCounter() {
        turnsSinceLastExtraction = 0;
    }

    /**
     * 获取当前轮次计数
     */
    public int getTurnsSinceLastExtraction() {
        return turnsSinceLastExtraction;
    }

    /**
     * 设置提取间隔
     */
    public void setExtractionInterval(int interval) {
        this.extractionInterval = interval;
    }
}
