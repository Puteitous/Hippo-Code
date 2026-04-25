package com.example.agent.tools;

import com.example.agent.core.di.ServiceLocator;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentTask;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;

public class CancelSubAgentTool implements ToolExecutor {
    private SubAgentManager subAgentManager;

    public CancelSubAgentTool() {
    }

    private SubAgentManager getManager() {
        if (subAgentManager == null) {
            subAgentManager = ServiceLocator.getOrNull(SubAgentManager.class);
        }
        return subAgentManager;
    }

    @Override
    public String getName() {
        return "cancel_subagent";
    }

    @Override
    public String getDescription() {
        return "取消正在执行的 Sub-Agent 子任务。用于终止耗时过长或不再需要的子任务，" +
               "支持取消单个特定任务或批量取消所有运行中的任务。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "task_id": {
                        "type": "string",
                        "description": "要取消的任务 ID（单个任务取消）"
                    },
                    "cancel_all": {
                        "type": "boolean",
                        "description": "是否取消所有运行中的任务。true=取消全部，false=仅取消指定 task_id",
                        "default": false
                    }
                },
                "required": ["task_id"]
            }
            """;
    }

    @Override
    public List<String> getAffectedPaths(JsonNode arguments) {
        return Collections.singletonList(".");
    }

    @Override
    public boolean requiresFileLock() {
        return false;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        SubAgentManager manager = getManager();
        if (manager == null) {
            return "⚠️ SubAgentManager 未初始化，暂无子任务";
        }

        boolean cancelAll = arguments.has("cancel_all") && arguments.get("cancel_all").asBoolean();
        
        if (cancelAll) {
            List<SubAgentTask> runningTasks = manager.getAllTasks().stream()
                .filter(t -> t.getStatus().name().equals("RUNNING"))
                .toList();
            
            if (runningTasks.isEmpty()) {
                return "📋 没有正在运行的子任务需要取消";
            }
            
            int cancelled = 0;
            for (SubAgentTask task : runningTasks) {
                if (task.cancel()) {
                    cancelled++;
                }
            }
            
            return "✅ 已批量取消 " + cancelled + "/" + runningTasks.size() + " 个运行中的子任务";
        }

        String taskId = arguments.has("task_id") && !arguments.get("task_id").isNull()
            ? arguments.get("task_id").asText()
            : null;
            
        if (taskId == null || taskId.isBlank()) {
            return "❌ 请提供 task_id 参数指定要取消的任务，或设置 cancel_all=true 取消全部";
        }

        SubAgentTask task = manager.getTask(taskId);
        if (task == null) {
            return "❌ 未找到任务 ID: " + taskId;
        }

        if (task.getStatus().name().equals("COMPLETED") || 
            task.getStatus().name().equals("FAILED") ||
            task.getStatus().name().equals("CANCELLED")) {
            return "⚠️ 任务 [" + taskId + "] 已结束 (状态: " + task.getStatus() + ")，无需取消";
        }

        if (task.cancel()) {
            return "✅ 任务 [" + taskId + "] 已成功取消\n" +
                   "   任务: " + task.getDescription();
        } else {
            return "⚠️ 任务 [" + taskId + "] 取消失败，可能已被取消或已结束";
        }
    }
}
