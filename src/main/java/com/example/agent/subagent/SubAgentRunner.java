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
                if (task.shouldStopExecution()) {
                    String reason = task.isCancelled() ? "任务已被取消" : "执行超时 (" + task.getTimeoutSeconds() + " 秒)";
                    task.addLog("执行终止: " + reason);
                    subAgentLogger.log("执行终止: " + reason);
                    if (task.isTimeout()) {
                        task.markFailed(new Exception("执行超时"));
                    } else {
                        task.markCompleted();
                    }
                    break;
                }

                turnCount++;
                task.addLog("\n--- 第 " + turnCount + " 轮 ---");
                subAgentLogger.log("--- 第 " + turnCount + " 轮 ---");

                List<Message> context = conversationService.prepareForInference(task.getConversation());
                
                String forcedSystemPrompt = buildForcedSystemPrompt(task.getDescription());
                List<Message> finalContext = new ArrayList<>();
                finalContext.add(Message.system(forcedSystemPrompt));
                
                finalContext.addAll(context.stream()
                    .filter(m -> !m.isSystem())
                    .collect(Collectors.toList()));
                
                subAgentLogger.log("上下文消息数: " + finalContext.size() + " (强制构建 Sub-Agent System Prompt)");
                subAgentLogger.log("System Prompt 长度: " + forcedSystemPrompt.length() + " 字符");
                subAgentLogger.log("任务描述: " + task.getDescription());
                List<Tool> allowedTools = getFilteredTools();
                subAgentLogger.log("可用工具数: " + allowedTools.size());

                ChatResponse response = llmClient.chat(finalContext, allowedTools);
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

    private String buildForcedSystemPrompt(String task) {
        return "你是一个严谨的子任务执行助手。必须严格遵守以下规则:\n\n" +
            "## 🎯 你的任务\n" +
            task + "\n\n" +
            "## ⚠️ 绝对强制规则（违反将导致任务失败）\n" +
            "1. ✅ **必须调用工具获取真实数据** - 所有信息必须通过工具调用获得\n" +
            "2. ❌ **严禁编造任何结果** - 不知道就调用工具，绝对不能想象、假设、编造\n" +
            "3. ❌ **严禁直接回答** - 你没有本地知识，不调用工具给出的任何答案都是错误的\n" +
            "4. ✅ **必须使用工具** - 你只能通过以下工具完成任务: read_file, glob, grep, search_code, list_directory, list_subagents\n" +
            "5. ✅ **如实汇报结果** - 工具返回什么就总结什么，不要添加任何工具未返回的信息\n\n" +
            "## 📋 执行流程\n" +
            "1. 分析任务需要哪些数据\n" +
            "2. 调用对应的工具获取真实数据\n" +
            "3. 根据工具的实际返回结果进行总结\n" +
            "4. 明确标注哪些文件已检查，哪些结果已验证\n\n" +
            "记住: 你是「工具执行者」，不是「答案生成者」。不调用工具 = 任务失败！";
    }

    private List<Tool> getFilteredTools() {
        List<Tool> allTools = toolRegistry.toTools();
        subAgentLogger.log("注册的总工具数: " + allTools.size());
        
        List<Tool> filtered = allTools.stream()
            .filter(tool -> {
                String name = tool.getFunction().getName();
                boolean allowed = permissionFilter.isToolAllowed(name);
                return allowed;
            })
            .collect(Collectors.toList());
        
        subAgentLogger.log("过滤后可用工具: " + filtered.stream()
            .map(t -> t.getFunction().getName())
            .collect(Collectors.toList()));
        
        return filtered;
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
