package com.example.agent.subagent;

import com.example.agent.core.AgentContext;
import com.example.agent.core.concurrency.ThreadPools;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.application.ConversationService;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.subagent.event.SubAgentCompletedEvent;
import com.example.agent.subagent.event.SubAgentFailedEvent;
import com.example.agent.subagent.event.SubAgentStartedEvent;
import com.example.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SubAgentManager {
    private static final Logger logger = LoggerFactory.getLogger(SubAgentManager.class);

    private final Map<String, SubAgentTask> activeTasks;
    private final Map<String, SubAgentLogger> loggers;
    private final ConversationService conversationService;
    private final LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private final SubAgentPermissionFilter permissionFilter;

    public SubAgentManager() {
        this.activeTasks = new ConcurrentHashMap<>();
        this.loggers = new ConcurrentHashMap<>();
        this.conversationService = ServiceLocator.get(ConversationService.class);
        this.llmClient = ServiceLocator.get(LlmClient.class);
        this.permissionFilter = new SubAgentPermissionFilter();
    }

    private ToolRegistry getToolRegistry() {
        if (toolRegistry == null) {
            toolRegistry = ServiceLocator.get(ToolRegistry.class);
            logger.info("SubAgentManager 延迟加载 ToolRegistry: 注册工具数 = {}", toolRegistry.toTools().size());
        }
        return toolRegistry;
    }

    public SubAgentTask forkAgent(String taskDescription, String systemPrompt) {
        String parentSessionId = getParentSessionId();
        
        Conversation subConversation = conversationService.createSubAgentConversation(
            buildSubAgentSystemPrompt(taskDescription, systemPrompt),
            parentSessionId
        );

        SubAgentTask task = new SubAgentTask(taskDescription, subConversation);
        activeTasks.put(task.getTaskId(), task);

        SubAgentLogger subAgentLogger = new SubAgentLogger(task, parentSessionId);
        loggers.put(task.getTaskId(), subAgentLogger);
        subAgentLogger.logStatusChange(SubAgentStatus.PENDING);
        subAgentLogger.log("Sub-Agent 会话 ID: " + subConversation.getSessionId());
        subAgentLogger.log("父会话 ID: " + parentSessionId);
        subAgentLogger.log("启动 Sub-Agent: " + taskDescription);
        subAgentLogger.log("日志目录: " + subAgentLogger.getLogDir());

        startExecution(task, subAgentLogger);

        logger.info("SubAgent 已启动: taskId={}, 会话={}, 日志={}, description={}",
            task.getTaskId(), subConversation.getSessionId(), 
            subAgentLogger.getLogDir(), taskDescription);
        return task;
    }

    private void startExecution(SubAgentTask task, SubAgentLogger subAgentLogger) {
        SubAgentRunner runner = new SubAgentRunner(
            task,
            subAgentLogger,
            llmClient,
            getToolRegistry(),
            conversationService,
            permissionFilter
        );

        ThreadPools.asyncGeneral().submit(() -> {
            try {
                task.setStatus(SubAgentStatus.RUNNING);
                subAgentLogger.logStatusChange(SubAgentStatus.RUNNING);
                com.example.agent.core.event.EventBus.publish(
                    new SubAgentStartedEvent(task.getTaskId(), task.getDescription())
                );

                runner.run();

                task.setStatus(SubAgentStatus.COMPLETED);
                String resultSummary = extractResultSummary(task);
                task.setResultSummary(resultSummary);
                subAgentLogger.logStatusChange(SubAgentStatus.COMPLETED);
                subAgentLogger.log("执行结果: " + resultSummary);
                subAgentLogger.saveDetails();

                task.markCompleted();
                injectResultToParentSession(task, resultSummary, null);

                com.example.agent.core.event.EventBus.publish(
                    new SubAgentCompletedEvent(task.getTaskId(), task.getDescription(), resultSummary)
                );

            } catch (Exception e) {
                logger.error("SubAgent 执行异常: taskId={}", task.getTaskId(), e);
                task.setStatus(SubAgentStatus.FAILED);
                task.setError(e);
                subAgentLogger.logStatusChange(SubAgentStatus.FAILED);
                subAgentLogger.logError("执行失败", e);
                subAgentLogger.saveDetails();

                task.markFailed(e);
                injectResultToParentSession(task, null, e.getMessage());

                com.example.agent.core.event.EventBus.publish(
                    new SubAgentFailedEvent(task.getTaskId(), task.getDescription(), e.getMessage())
                );
            } finally {
                cleanupExpiredTasks();
            }
        });
    }

    private String extractResultSummary(SubAgentTask task) {
        List<com.example.agent.llm.model.Message> messages = task.getConversation().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            com.example.agent.llm.model.Message msg = messages.get(i);
            if (msg.isAssistant() && msg.getContent() != null && !msg.getContent().isBlank()) {
                String content = msg.getContent().trim();
                return content.length() > 500 ? content.substring(0, 500) + "..." : content;
            }
        }
        return "任务执行完成";
    }

    private void injectResultToParentSession(SubAgentTask task, String result, String error) {
        String parentSessionId = getParentSessionId();
        if (parentSessionId == null) {
            return;
        }

        try {
            com.example.agent.core.AgentContext ctx = ServiceLocator.getOrNull(com.example.agent.core.AgentContext.class);
            if (ctx != null && ctx.getConversation() != null) {
                String status = error != null ? "失败" : "成功";
                String marker = error != null ? "❌" : "✅";
                String messageContent = String.format("""
                    
                    %s === Sub-Agent 任务执行%s ===
                    任务 ID: %s
                    任务描述: %s
                    %s
                    
                    """,
                    marker, status,
                    task.getTaskId(),
                    task.getDescription(),
                    error != null ? "错误信息: " + error : "执行结果: " + result
                );

                ctx.getConversation().addMessage(
                    com.example.agent.llm.model.Message.system(messageContent)
                );

                logger.debug("Sub-Agent 结果已注入父会话: taskId={}", task.getTaskId());
            }
        } catch (Exception e) {
            logger.warn("注入 Sub-Agent 结果到父会话失败: {}", e.getMessage());
        }
    }

    private String getParentSessionId() {
        try {
            AgentContext ctx = ServiceLocator.getOrNull(AgentContext.class);
            if (ctx != null) {
                return ctx.getSessionId();
            }
        } catch (Exception e) {
            // 忽略，使用默认路径
        }
        return null;
    }

    private String buildSubAgentSystemPrompt(String task, String customPrompt) {
        if (customPrompt != null && !customPrompt.isBlank()) {
            return customPrompt;
        }
        
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

    private void cleanupExpiredTasks() {
        activeTasks.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public SubAgentTask getTask(String taskId) {
        return activeTasks.get(taskId);
    }

    public List<SubAgentTask> getAllTasks() {
        cleanupExpiredTasks();
        return List.copyOf(activeTasks.values());
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }
}
