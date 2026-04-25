package com.example.agent.tools;

import com.example.agent.core.di.ServiceLocator;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentTask;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ListSubAgentsTool implements ToolExecutor {
    private SubAgentManager subAgentManager;

    public ListSubAgentsTool() {
    }

    private SubAgentManager getManager() {
        if (subAgentManager == null) {
            subAgentManager = ServiceLocator.getOrNull(SubAgentManager.class);
        }
        return subAgentManager;
    }

    @Override
    public String getName() {
        return "list_subagents";
    }

    @Override
    public String getDescription() {
        return "查询所有 Sub-Agent 子任务的执行状态和结果。可以查看正在执行的任务、已完成的任务、以及各任务的执行详情和结果摘要。" +
               "适用于批量检查和管理子任务执行进度。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "status": {
                        "type": "string",
                        "description": "按状态过滤: RUNNING=运行中, COMPLETED=已完成, FAILED=失败, ALL=全部",
                        "enum": ["RUNNING", "COMPLETED", "FAILED", "ALL"],
                        "default": "ALL"
                    },
                    "task_id": {
                        "type": "string",
                        "description": "查询单个特定任务 ID 的详细信息"
                    }
                }
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

        String taskId = arguments.has("task_id") && !arguments.get("task_id").isNull()
            ? arguments.get("task_id").asText()
            : null;
        String statusFilter = arguments.has("status") && !arguments.get("status").isNull()
            ? arguments.get("status").asText()
            : "ALL";

        if (taskId != null) {
            SubAgentTask task = manager.getTask(taskId);
            if (task == null) {
                return "❌ 未找到任务 ID: " + taskId;
            }
            return formatTaskDetail(task);
        }

        List<SubAgentTask> tasks = manager.getAllTasks();
        List<SubAgentTask> filtered = filterByStatus(tasks, statusFilter);

        if (filtered.isEmpty()) {
            return "📋 暂无 " + statusFilter + " 状态的子任务";
        }

        return formatTaskList(filtered, statusFilter);
    }

    private List<SubAgentTask> filterByStatus(List<SubAgentTask> tasks, String statusFilter) {
        if ("ALL".equals(statusFilter)) {
            return tasks;
        }
        return tasks.stream()
            .filter(t -> t.getStatus().name().equals(statusFilter))
            .collect(Collectors.toList());
    }

    private String formatTaskList(List<SubAgentTask> tasks, String statusFilter) {
        long waiting = tasks.stream().filter(t -> t.getStatus().name().equals("WAITING")).count();
        long running = tasks.stream().filter(t -> t.getStatus().name().equals("RUNNING")).count();
        long completed = tasks.stream().filter(t -> t.getStatus().name().equals("COMPLETED")).count();
        long failed = tasks.stream().filter(t -> t.getStatus().name().equals("FAILED")).count();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Sub-Agent 任务汇总 (").append(statusFilter).append(")\n");
        sb.append("   总计: ").append(tasks.size()).append(" 个任务\n");
        if (waiting > 0) sb.append("   等待依赖: ").append(waiting).append(" | ");
        sb.append("执行中: ").append(running).append(" | ");
        sb.append("已完成: ").append(completed).append(" | ");
        sb.append("失败: ").append(failed).append("\n\n");

        for (SubAgentTask task : tasks) {
            String marker = task.getStatus().name().equals("COMPLETED") ? "✅" :
                           task.getStatus().name().equals("FAILED") ? "❌" :
                           task.getStatus().name().equals("WAITING") ? "⏳" : "🔄";
            sb.append(marker).append(" [").append(task.getTaskId()).append("] ");
            sb.append("[").append(task.getStatus()).append("]\n");
            sb.append("   任务: ").append(truncate(task.getDescription(), 60)).append("\n");
            if (task.hasDependencies()) {
                sb.append("   依赖: ").append(task.getDependsOn()).append("\n");
            }
            if (task.getResultSummary() != null && task.getStatus().name().equals("COMPLETED")) {
                sb.append("   结果: ").append(truncate(task.getResultSummary(), 100)).append("\n");
            }
            if (task.getError() != null) {
                sb.append("   错误: ").append(task.getError().getMessage()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatTaskDetail(SubAgentTask task) {
        StringBuilder sb = new StringBuilder();
        String marker = task.getStatus().name().equals("COMPLETED") ? "✅" :
                       task.getStatus().name().equals("FAILED") ? "❌" : "🔄";
        
        sb.append(marker).append(" === Sub-Agent 任务详情 ===\n");
        sb.append("   Task ID: ").append(task.getTaskId()).append("\n");
        sb.append("   状态: ").append(task.getStatus()).append("\n");
        sb.append("   任务: ").append(task.getDescription()).append("\n\n");

        if (task.getResultSummary() != null) {
            sb.append("=== 执行结果 ===\n");
            sb.append(task.getResultSummary()).append("\n");
        }

        if (task.getError() != null) {
            sb.append("\n=== 错误信息 ===\n");
            sb.append(task.getError().getMessage()).append("\n");
        }

        sb.append("\n=== 执行日志摘要 (最近 5 条) ===\n");
        List<String> logs = task.getOutputLog();
        int start = Math.max(0, logs.size() - 5);
        for (int i = start; i < logs.size(); i++) {
            sb.append(" ").append(logs.get(i)).append("\n");
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() > maxLength ? s.substring(0, maxLength) + "..." : s;
    }
}
