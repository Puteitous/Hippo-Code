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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SubAgentManager {
    private static final Logger logger = LoggerFactory.getLogger(SubAgentManager.class);

    private final Map<String, SubAgentTask> activeTasks;
    private final Map<String, SubAgentLogger> loggers;
    private final ConversationService conversationService;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final SubAgentPermissionFilter permissionFilter;

    public SubAgentManager() {
        this.activeTasks = new ConcurrentHashMap<>();
        this.loggers = new ConcurrentHashMap<>();
        this.conversationService = ServiceLocator.get(ConversationService.class);
        this.llmClient = ServiceLocator.get(LlmClient.class);
        this.toolRegistry = ServiceLocator.get(ToolRegistry.class);
        this.permissionFilter = new SubAgentPermissionFilter();
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
            toolRegistry,
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
                task.setResultSummary("任务完成");
                subAgentLogger.logStatusChange(SubAgentStatus.COMPLETED);
                subAgentLogger.saveDetails();
                com.example.agent.core.event.EventBus.publish(
                    new SubAgentCompletedEvent(task.getTaskId(), task.getDescription(), "任务执行完成")
                );

            } catch (Exception e) {
                logger.error("SubAgent 执行异常: taskId={}", task.getTaskId(), e);
                task.setStatus(SubAgentStatus.FAILED);
                task.setError(e);
                subAgentLogger.logStatusChange(SubAgentStatus.FAILED);
                subAgentLogger.logError("执行失败", e);
                subAgentLogger.saveDetails();
                com.example.agent.core.event.EventBus.publish(
                    new SubAgentFailedEvent(task.getTaskId(), task.getDescription(), e.getMessage())
                );
            } finally {
                cleanupExpiredTasks();
            }
        });
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
        String basePrompt = customPrompt != null && !customPrompt.isBlank() ? customPrompt :
            "你是一个专注的子任务执行助手。你的任务是: " + task + "\n\n" +
            "重要约束:\n" +
            "1. 专注完成分配给你的任务，不要超出范围\n" +
            "2. 只能使用允许的工具: 文件读取、代码搜索、信息查询\n" +
            "3. 将执行结果清晰地总结出来\n" +
            "4. 遇到不确定的地方不要猜测，如实报告\n" +
            "5. 完成后给出明确的完成总结";

        return basePrompt;
    }

    private void cleanupExpiredTasks() {
        activeTasks.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public SubAgentTask getTask(String taskId) {
        return activeTasks.get(taskId);
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }
}
