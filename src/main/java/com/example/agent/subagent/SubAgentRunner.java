package com.example.agent.subagent;

import com.example.agent.application.ConversationService;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Tool;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.subagent.event.SubAgentProgressEvent;
import com.example.agent.tools.ToolExecutor;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SubAgentRunner implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SubAgentRunner.class);
    private static final int MAX_TURNS = 10;
    private static final int MAX_EMPTY_RESPONSE_RETRIES = 2;

    private final SubAgentTask task;
    private final SubAgentLogger subAgentLogger;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConversationService conversationService;
    private final SubAgentPermissionFilter permissionFilter;
    private final ObjectMapper objectMapper;

    public SubAgentRunner(SubAgentTask task,
                          SubAgentLogger subAgentLogger,
                          LlmClient llmClient,
                          ToolRegistry toolRegistry,
                          ConversationService conversationService,
                          SubAgentPermissionFilter permissionFilter) {
        this.task = task;
        this.subAgentLogger = subAgentLogger;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.conversationService = conversationService;
        this.permissionFilter = permissionFilter;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run() {
        int turnCount = 0;
        int emptyResponseRetries = 0;

        task.addLog("=== SubAgent 启动: " + task.getDescription() + " ===");
        publishProgress("任务启动，开始执行...");
        subAgentLogger.log("=== SubAgent 启动 ===");
        subAgentLogger.log("任务描述: " + task.getDescription());

        try {
            while (turnCount < MAX_TURNS) {
                turnCount++;
                task.addLog("\n--- 第 " + turnCount + " 轮 ---");
                subAgentLogger.log("--- 第 " + turnCount + " 轮 ---");

                List<Message> context = conversationService.prepareForInference(task.getConversation());
                subAgentLogger.log("上下文消息数: " + context.size());
                List<Tool> allowedTools = getFilteredTools();
                subAgentLogger.log("可用工具数: " + allowedTools.size());

                ChatResponse response = llmClient.chat(context, allowedTools);
                subAgentLogger.log("LLM 调用完成");

                Message assistantMessage = response.getFirstMessage();
                if (assistantMessage == null) {
                    task.addLog("未收到 LLM 响应");
                    subAgentLogger.log("警告: 未收到 LLM 响应");
                    if (emptyResponseRetries++ < MAX_EMPTY_RESPONSE_RETRIES) {
                        continue;
                    }
                    break;
                }

                conversationService.addAssistantMessage(task.getConversation(), assistantMessage, response.getUsage());

                if (assistantMessage.getContent() != null && !assistantMessage.getContent().isBlank()) {
                    task.addLog("AI: " + assistantMessage.getContent());
                    subAgentLogger.logAiResponse(assistantMessage.getContent());
                    publishProgress(assistantMessage.getContent());
                }

                if (response.hasToolCalls()) {
                    List<ToolCall> toolCalls = assistantMessage.getToolCalls();
                    task.addLog("工具调用数量: " + toolCalls.size());
                    subAgentLogger.log("准备执行工具调用: " + toolCalls.size() + " 个");
                    publishProgress("执行 " + toolCalls.size() + " 个工具调用...");

                    executeToolCalls(toolCalls);
                } else {
                    task.addLog("任务完成，无更多工具调用");
                    subAgentLogger.log("无工具调用，任务完成");
                    break;
                }
            }

            if (turnCount >= MAX_TURNS) {
                task.addLog("达到最大轮次限制: " + MAX_TURNS);
                subAgentLogger.log("达到最大轮次限制: " + MAX_TURNS);
            }

            task.addLog("=== SubAgent 执行结束 ===");
            subAgentLogger.log("=== SubAgent 执行结束 ===");

        } catch (Exception e) {
            task.addLog("执行异常: " + e.getMessage());
            subAgentLogger.logError("执行异常", e);
            logger.error("SubAgentRunner 执行异常: taskId={}", task.getTaskId(), e);
            throw new RuntimeException("SubAgent 执行失败", e);
        }
    }

    private List<Tool> getFilteredTools() {
        return toolRegistry.toTools().stream()
            .filter(tool -> permissionFilter.isToolAllowed(tool.getFunction().getName()))
            .collect(Collectors.toList());
    }

    private void executeToolCalls(List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            String toolName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();

            subAgentLogger.logToolCall(toolName, arguments);

            if (!permissionFilter.isToolAllowed(toolName)) {
                String errorMsg = "SubAgent 不允许执行工具: " + toolName;
                task.addLog(errorMsg);
                subAgentLogger.log("权限拒绝: " + errorMsg);
                addToolResult(toolCall.getId(), toolName, errorMsg);
                continue;
            }

            try {
                ToolExecutor executor = toolRegistry.getExecutor(toolName);
                JsonNode args = objectMapper.readTree(arguments);

                task.addLog("执行工具: " + toolName);
                publishProgress("执行工具: " + toolName);

                String result = executor.execute(args);
                task.addLog("工具结果: " + truncate(result, 200));
                subAgentLogger.logToolResult(toolName, result);

                addToolResult(toolCall.getId(), toolName, result);

            } catch (Exception e) {
                String errorMsg = "工具执行失败 " + toolName + ": " + e.getMessage();
                task.addLog(errorMsg);
                subAgentLogger.logError(errorMsg, e);
                addToolResult(toolCall.getId(), toolName, errorMsg);
            }
        }
    }

    private void addToolResult(String toolCallId, String toolName, String content) {
        String compressed = conversationService.getToolResultCompressor().compress(content);
        task.getConversation().addMessage(Message.toolResult(toolCallId, toolName, compressed));
    }

    private void publishProgress(String message) {
        com.example.agent.core.event.EventBus.publish(
            new SubAgentProgressEvent(task.getTaskId(), task.getDescription(), message)
        );
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "null";
        return s.length() > maxLength ? s.substring(0, maxLength) + "..." : s;
    }
}
