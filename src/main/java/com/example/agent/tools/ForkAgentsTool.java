package com.example.agent.tools;

import com.example.agent.core.di.ServiceLocator;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentTask;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ForkAgentsTool implements ToolExecutor {
    private SubAgentManager subAgentManager;

    public ForkAgentsTool() {
    }

    private SubAgentManager getManager() {
        if (subAgentManager == null) {
            subAgentManager = ServiceLocator.getOrNull(SubAgentManager.class);
        }
        return subAgentManager;
    }

    @Override
    public String getName() {
        return "fork_agents";
    }

    @Override
    public String getDescription() {
        return "批量创建多个子 Agent 并行执行独立任务。一次可以同时启动 2-10 个 Sub-Agent，" +
               "各自执行不同的子任务（如扫描不同的模块、读取多个文件、并行搜索）。" +
               "所有子 Agent 拥有独立的上下文和工具沙箱，互不干扰，大幅提升效率。" +
               "适用于大规模并行代码分析、多文件读取、分模块扫描等场景。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "tasks": {
                        "type": "array",
                        "description": "子任务列表，每个对象包含独立的 task 和可选的 system_prompt",
                        "items": {
                            "type": "object",
                            "properties": {
                                "task": {
                                    "type": "string",
                                    "description": "该子任务的详细描述"
                                },
                                "system_prompt": {
                                    "type": "string",
                                    "description": "该子任务的自定义系统提示词（可选）"
                                },
                                "timeout_seconds": {
                                    "type": "integer",
                                    "description": "该子任务的超时时间（秒），默认 300 秒（5分钟）",
                                    "default": 300
                                }
                            }
                        }
                    },
                    "wait_for_all": {
                        "type": "boolean",
                        "description": "是否等待所有子任务完成后再返回结果。true=阻塞等待全部完成，false=后台异步",
                        "default": false
                    },
                    "wait_timeout_seconds": {
                        "type": "integer",
                        "description": "等待所有任务完成的超时时间（秒），默认 180 秒",
                        "default": 180
                    }
                },
                "required": ["tasks"]
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
    public boolean shouldRunInBackground() {
        return false;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        SubAgentManager manager = getManager();
        if (manager == null) {
            throw new ToolExecutionException("SubAgentManager 未初始化");
        }

        if (!arguments.has("tasks") || !arguments.get("tasks").isArray()) {
            throw new ToolExecutionException("缺少必需参数: tasks (数组类型)");
        }

        JsonNode tasksNode = arguments.get("tasks");
        boolean waitForAll = arguments.has("wait_for_all") && arguments.get("wait_for_all").asBoolean();
        int waitTimeoutSeconds = arguments.has("wait_timeout_seconds") 
            ? arguments.get("wait_timeout_seconds").asInt() 
            : 180;

        List<SubAgentTask> launchedTasks = new ArrayList<>();

        for (JsonNode taskNode : tasksNode) {
            if (!taskNode.has("task") || taskNode.get("task").isNull()) {
                continue;
            }
            String task = taskNode.get("task").asText();
            String systemPrompt = taskNode.has("system_prompt") && !taskNode.get("system_prompt").isNull()
                ? taskNode.get("system_prompt").asText()
                : null;
            
            int taskTimeoutSeconds = 300;
            if (taskNode.has("timeout_seconds") && !taskNode.get("timeout_seconds").isNull()) {
                taskTimeoutSeconds = taskNode.get("timeout_seconds").asInt();
                taskTimeoutSeconds = Math.max(30, Math.min(3600, taskTimeoutSeconds));
            }
            
            SubAgentTask subTask = manager.forkAgent(task, systemPrompt, taskTimeoutSeconds);
            launchedTasks.add(subTask);
        }

        if (launchedTasks.isEmpty()) {
            return "❌ 没有有效的任务可以启动";
        }

        if (!waitForAll) {
            return buildAsyncResult(launchedTasks);
        }

        return waitForAllResults(launchedTasks, waitTimeoutSeconds);
    }

    private String buildAsyncResult(List<SubAgentTask> tasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 批量启动 ").append(tasks.size()).append(" 个 Sub-Agent\n");
        sb.append("执行模式: 后台并行执行\n\n");
        
        for (int i = 0; i < tasks.size(); i++) {
            SubAgentTask task = tasks.get(i);
            sb.append("  [").append(i + 1).append("] Task ID: ").append(task.getTaskId()).append("\n");
            sb.append("      任务: ").append(truncate(task.getDescription(), 50)).append("\n");
        }
        
        sb.append("\n📋 使用建议:\n");
        sb.append("   - 调用 list_subagents 查看实时进度\n");
        sb.append("   - 调用 list_subagents task_id=xxx 查看单个任务详情\n");
        
        return sb.toString();
    }

    private String waitForAllResults(List<SubAgentTask> tasks, int timeoutSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 等待 ").append(tasks.size()).append(" 个 Sub-Agent 并行执行...\n");
        sb.append("执行模式: 同步等待全部完成\n");
        sb.append("超时时间: ").append(timeoutSeconds).append(" 秒\n\n");

        int completed = 0;
        int failed = 0;
        int timeout = 0;

        for (SubAgentTask task : tasks) {
            try {
                SubAgentTask finished = task.awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
                if (finished.getError() != null) {
                    failed++;
                } else {
                    completed++;
                }
            } catch (Exception e) {
                timeout++;
            }
        }

        sb.append("=== 执行汇总 ===\n");
        sb.append("   总计: ").append(tasks.size()).append(" 个任务\n");
        sb.append("   ✅ 成功: ").append(completed).append("\n");
        sb.append("   ❌ 失败: ").append(failed).append("\n");
        sb.append("   ⏱️ 超时: ").append(timeout).append("\n\n");

        for (SubAgentTask task : tasks) {
            String marker = task.getStatus().name().equals("COMPLETED") ? "✅" :
                           task.getStatus().name().equals("FAILED") ? "❌" : "⏱️";
            
            sb.append(marker).append(" [").append(task.getTaskId()).append("]\n");
            sb.append("   任务: ").append(truncate(task.getDescription(), 50)).append("\n");
            
            if (task.getResultSummary() != null) {
                sb.append("   结果: ").append(truncate(task.getResultSummary(), 150)).append("\n");
            }
            if (task.getError() != null) {
                sb.append("   错误: ").append(task.getError().getMessage()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("\n💡 提示:\n");
        sb.append("   调用 list_subagents 获取所有任务的完整执行结果和日志");

        return sb.toString();
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() > maxLength ? s.substring(0, maxLength) + "..." : s;
    }
}
