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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SubAgentRunner implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SubAgentRunner.class);
    private static final int MAX_TURNS = 10;
    private static final int MAX_EMPTY_RESPONSE_RETRIES = 3;
    private static final int MAX_LLM_ERROR_RETRIES = 3;
    private static final int MAX_TOOL_ERROR_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 1000;

    private final SubAgentTask task;
    private final SubAgentLogger subAgentLogger;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConversationService conversationService;
    private final SubAgentPermission permission;
    private final ObjectMapper objectMapper;

    public SubAgentRunner(SubAgentTask task,
                          SubAgentLogger subAgentLogger,
                          LlmClient llmClient,
                          ToolRegistry toolRegistry,
                          ConversationService conversationService,
                          SubAgentPermission permission) {
        this.task = task;
        this.subAgentLogger = subAgentLogger;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.conversationService = conversationService;
        this.permission = permission;
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
                List<Message> finalContext = new ArrayList<>();
                String systemPromptInfo;
                
                if (task.isForkChild()) {
                    List<Message> forkContext = new ArrayList<>();
                    forkContext.addAll(context);
                    
                    String forkTaskInstruction = String.format(
                        "## 🎯 子代理任务（Fork 模式）\n\n**任务描述:** %s\n\n---\n\n## ⚠️ 绝对强制规则（必须遵守！）\n\n1. ✅ **结果明确立即结束** - 一旦获得确定结果，立刻输出最终总结，不再调用工具\n2. ✅ **不要过度优化格式** - 不需要做漂亮的表格，简洁给出结果即可\n3. ❌ **严禁无意义重试** - 同样参数的工具最多调用 1 次，重复毫无意义\n4. ❌ **严禁询问用户** - 绝对不能问用户任何问题，信息不足就如实报告\n5. ✅ **最少轮次原则** - 能用 1 轮完成就不要用 2 轮，节省时间和 Token\n\n---\n\n请直接开始执行。",
                        task.getDescription()
                    );
                    forkContext.add(Message.user(forkTaskInstruction));
                    finalContext = forkContext;
                    
                    systemPromptInfo = "Fork 模式: 复用父 Agent System Prompt (缓存优化)";
                    subAgentLogger.log("上下文消息数: " + finalContext.size() + " (Fork 模式 - 完整复用上下文，缓存优化)");
                } else {
                    String forcedSystemPrompt = buildForcedSystemPrompt(task.getDescription());
                    finalContext.add(Message.system(forcedSystemPrompt));
                    
                    finalContext.addAll(context.stream()
                        .filter(m -> !m.isSystem())
                        .collect(Collectors.toList()));
                    
                    boolean hasUserMessage = finalContext.stream().anyMatch(Message::isUser);
                    if (!hasUserMessage) {
                        finalContext.add(Message.user("请执行任务。"));
                    }
                    
                    systemPromptInfo = "独立模式: 强制构建 Sub-Agent System Prompt, 长度: " + forcedSystemPrompt.length() + " 字符";
                    subAgentLogger.log("上下文消息数: " + finalContext.size() + " (独立模式)");
                }
                
                boolean isFinalRound = (turnCount == MAX_TURNS - 1);
                if (isFinalRound) {
                    finalContext.add(Message.user("这是最后一轮执行机会，请基于所有工具调用结果，输出最终总结，不能再调用任何工具！"));
                    subAgentLogger.log("最后一轮 - 禁用工具，强制总结");
                }
                
                subAgentLogger.log(systemPromptInfo);
                subAgentLogger.log("任务描述: " + task.getDescription());
                List<Tool> allowedTools = isFinalRound ? Collections.emptyList() : getFilteredTools();
                subAgentLogger.log("可用工具数: " + allowedTools.size() + (isFinalRound ? " (最后一轮禁用工具，强制总结)" : ""));

                ChatResponse response = null;
                int llmRetryCount = 0;
                while (llmRetryCount < MAX_LLM_ERROR_RETRIES) {
                    try {
                        response = llmClient.chat(finalContext, allowedTools);
                        break;
                    } catch (Exception e) {
                        llmRetryCount++;
                        String msg = String.format("LLM 调用异常，第 %d/%d 次重试: %s", 
                            llmRetryCount, MAX_LLM_ERROR_RETRIES, e.getMessage());
                        task.addLog(msg);
                        subAgentLogger.log(msg);
                        if (llmRetryCount >= MAX_LLM_ERROR_RETRIES) {
                            throw new RuntimeException("LLM 调用失败，已达最大重试次数", e);
                        }
                        try {
                            Thread.sleep(RETRY_DELAY_MS * llmRetryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("执行被中断", ie);
                        }
                    }
                }
                subAgentLogger.log("LLM 调用完成" + (llmRetryCount > 0 ? " (重试 " + llmRetryCount + " 次)" : ""));

                if (response == null || response.getFirstMessage() == null) {
                    task.addLog("LLM 返回空响应");
                    subAgentLogger.log("警告: LLM 返回空响应");
                    if (emptyResponseRetries++ < MAX_EMPTY_RESPONSE_RETRIES) {
                        continue;
                    }
                    task.markFailed(new RuntimeException("LLM 多次返回空响应"));
                    break;
                }

                Message assistantMessage = response.getFirstMessage();

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
                    task.markCompleted();
                    break;
                }
            }

            if (turnCount >= MAX_TURNS) {
                task.addLog("达到最大轮次限制: " + MAX_TURNS);
                subAgentLogger.log("达到最大轮次限制: " + MAX_TURNS);
                if (!task.isDone()) {
                    task.markCompleted();
                }
            }

            task.addLog("=== SubAgent 执行结束 ===");
            subAgentLogger.log("=== SubAgent 执行结束 ===");

        } catch (Exception e) {
            task.addLog("执行异常: " + e.getMessage());
            subAgentLogger.logError("执行异常", e);
            logger.error("SubAgentRunner 执行异常: taskId={}", task.getTaskId(), e);
            if (!task.isDone()) {
                task.markFailed(e);
            }
        }
    }

    private String buildForcedSystemPrompt(String task) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个严谨的子任务执行助手。必须严格遵守以下规则:\n\n");
        sb.append("## 🎯 你的任务\n");
        sb.append(task).append("\n\n");
        sb.append("## ⚠️ 绝对强制规则（违反将导致任务失败）\n");
        
        if (permission.isRequireToolCalls()) {
            sb.append("1. ✅ **必须调用工具获取真实数据** - 所有信息必须通过工具调用获得\n");
            sb.append("2. ❌ **严禁编造任何结果** - 不知道就调用工具，绝对不能想象、假设、编造\n");
            sb.append("3. ❌ **严禁直接回答** - 你没有本地知识，不调用工具给出的任何答案都是错误的\n");
            sb.append("4. ❌ **严禁询问用户** - 绝对不能问用户任何问题、不能要求补充信息\n");
            sb.append("5. ❌ **禁止无意义循环** - 同样参数的工具最多调用 2 次，重复无济于事\n");
            sb.append("6. ✅ **信息不足就如实说** - 找不到就说「未找到」，信息不足就说「信息不足」，不要追问\n");
            sb.append("7. ✅ **结果明确立即结束** - 一旦获得确定结果，立刻输出最终总结，不再调用工具\n");
            sb.append("8. ✅ **必须使用工具** - 可用工具: ").append(permission.getAllowedTools()).append("\n");
            sb.append("9. ✅ **如实汇报结果** - 工具返回什么就总结什么，不要添加任何工具未返回的信息\n\n");
            sb.append("## 📋 执行流程\n");
            sb.append("1. 分析任务需要哪些数据\n");
            sb.append("2. 调用对应的工具获取真实数据\n");
            sb.append("3. 根据工具的实际返回结果进行总结\n");
            sb.append("4. 明确标注哪些文件已检查，哪些结果已验证\n\n");
            sb.append("记住: 你是「工具执行者」，不是「答案生成者」。\n");
            sb.append("信息不足 → 调用更多工具；找不到 → 如实报告；绝对不要问用户！\n");
            sb.append("不调用工具 + 询问用户 = 任务失败！");
        } else {
            sb.append("1. ✅ **优先使用工具** - 能通过工具获取的信息请调用工具\n");
            sb.append("2. ❌ **严禁编造任何结果** - 不知道就说不知道，绝对不能想象、假设、编造\n");
            sb.append("3. ❌ **严禁询问用户** - 绝对不能问用户任何问题、不能要求补充信息\n");
            sb.append("4. ✅ **可直接输出总结** - 基于已有对话上下文进行分析，不需要强制调用工具\n");
            sb.append("5. ✅ **可用工具**: ").append(permission.getAllowedTools()).append("\n");
            sb.append("6. ✅ **如实汇报结果** - 按要求格式输出，简洁准确\n\n");
            sb.append("## 📋 执行流程\n");
            sb.append("1. 阅读已有上下文，理解任务目标\n");
            sb.append("2. 如需读取/写入文件，使用对应工具\n");
            sb.append("3. 直接输出最终结果，不需要额外确认\n\n");
            sb.append("记住: 你是「分析者」，不是「工具执行者」。\n");
            sb.append("已有信息足够时 → 直接输出高质量结果；\n");
            sb.append("需要文件操作时 → 使用工具后再输出！");
        }
        
        return sb.toString();
    }

    private List<Tool> getFilteredTools() {
        List<Tool> allTools = toolRegistry.toTools();
        subAgentLogger.log("权限模式: " + permission.getName());
        subAgentLogger.log("注册的总工具数: " + allTools.size());
        
        List<Tool> filtered = allTools.stream()
            .filter(tool -> {
                String name = tool.getFunction().getName();
                boolean allowed = permission.isToolAllowed(name);
                return allowed;
            })
            .collect(Collectors.toList());
        
        if (task.isForkChild()) {
            int originalCount = filtered.size();
            filtered = filtered.stream()
                .filter(tool -> {
                    String name = tool.getFunction().getName();
                    return !"fork_agent".equals(name) && !"fork_agents".equals(name);
                })
                .collect(Collectors.toList());
            
            if (originalCount > filtered.size()) {
                subAgentLogger.log("Fork 递归防护: 已禁用 fork_agent/fork_agents 工具");
            }
        }
        
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

            if (!permission.isToolAllowed(toolName)) {
                String errorMsg = "SubAgent 不允许执行工具: " + toolName;
                task.addLog(errorMsg);
                subAgentLogger.log("权限拒绝: " + errorMsg);
                addToolResult(toolCall.getId(), toolName, errorMsg);
                continue;
            }

            String result = null;
            int toolRetryCount = 0;
            while (toolRetryCount < MAX_TOOL_ERROR_RETRIES) {
                try {
                    ToolExecutor executor = toolRegistry.getExecutor(toolName);
                    JsonNode args = objectMapper.readTree(arguments);

                    task.addLog("执行工具: " + toolName + (toolRetryCount > 0 ? " (重试 " + toolRetryCount + " 次)" : ""));
                    publishProgress("执行工具: " + toolName);

                    result = executor.execute(args);
                    break;
                } catch (Exception e) {
                    toolRetryCount++;
                    String msg = String.format("工具执行异常 [%s]，第 %d/%d 次重试: %s", 
                        toolName, toolRetryCount, MAX_TOOL_ERROR_RETRIES, e.getMessage());
                    task.addLog(msg);
                    subAgentLogger.log(msg);
                    if (toolRetryCount >= MAX_TOOL_ERROR_RETRIES) {
                        result = "工具执行失败: " + e.getMessage() + " (已达最大重试次数)";
                        subAgentLogger.logError("工具执行最终失败", e);
                        break;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS * toolRetryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            task.addLog("工具结果: " + truncate(result, 200));
            subAgentLogger.logToolResult(toolName, result);
            addToolResult(toolCall.getId(), toolName, result);
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
