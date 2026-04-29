package com.example.agent.tools;

import com.example.agent.core.di.ServiceLocator;
import com.example.agent.subagent.BuiltInAgent;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentResultFormatter;
import com.example.agent.subagent.SubAgentStatus;
import com.example.agent.subagent.SubAgentTask;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ForkAgentsTool implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ForkAgentsTool.class);
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
        SubAgentManager manager = getManager();
        String agentMenu = manager != null ? manager.getFullAgentMenu() : BuiltInAgent.getAgentMenu();
        
        return "🚀 批量创建多个专家子 Agent 并行执行独立任务。这是极致的成本优化！\n\n" +
               agentMenu + "\n" +
               "一次可以同时启动 2-10 个 Sub-Agent，各自执行不同的子任务。" +
               "所有子 Agent 共享同一个 180K tokens 缓存前缀，边际成本近乎为零！\n" +
               "适用于大规模并行代码分析、多文件读取、分模块扫描等场景。**推荐省略 subagent_type 以获得最大缓存收益！**";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "tasks": {
                        "type": "array",
                        "description": "子任务列表，每个对象包含独立的 task 描述和可选的专家类型",
                        "items": {
                            "type": "object",
                            "properties": {
                                "task": {
                                    "type": "string",
                                    "description": "该子任务的详细描述"
                                },
                                "subagent_type": {
                                    "type": "string",
                                    "description": "专家类型。**省略此参数 = 完整复用当前上下文（98% 缓存命中，强烈推荐）**。可选值: explore（代码搜索）, plan（方案设计）, verification（独立验证）, general（通用）"
                                },
                                "timeout_seconds": {
                                    "type": "integer",
                                    "description": "该子任务的超时时间（秒），默认 300 秒（5分钟）",
                                    "default": 300
                                },
                                "depends_on_index": {
                                    "type": "array",
                                    "items": { "type": "integer" },
                                    "description": "依赖的任务索引列表（在本次 tasks 数组中的位置），这些任务完成后本任务才会开始执行。示例: [0, 1] 表示本任务依赖第 0 和第 1 个任务"
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
        Map<Integer, Integer> indexToTaskPosition = new HashMap<>();

        int taskIndex = 0;
        Map<Integer, List<String>> taskDependencies = new HashMap<>();

        for (JsonNode taskNode : tasksNode) {
            if (!taskNode.has("task") || taskNode.get("task").isNull()) {
                taskIndex++;
                continue;
            }

            List<String> dependsOnTaskIds = new ArrayList<>();
            if (taskNode.has("depends_on_index") && taskNode.get("depends_on_index").isArray()) {
                for (JsonNode depIndexNode : taskNode.get("depends_on_index")) {
                    if (depIndexNode.isInt()) {
                        int depIndex = depIndexNode.asInt();
                        if (indexToTaskPosition.containsKey(depIndex)) {
                            Integer depPos = indexToTaskPosition.get(depIndex);
                            if (depPos < launchedTasks.size()) {
                                dependsOnTaskIds.add(launchedTasks.get(depPos).getTaskId());
                            }
                        }
                    }
                }
            }
            taskDependencies.put(taskIndex, dependsOnTaskIds);

            String task = taskNode.get("task").asText();
            String subagentType = taskNode.has("subagent_type") && !taskNode.get("subagent_type").isNull()
                ? taskNode.get("subagent_type").asText()
                : null;
            
            int taskTimeoutSeconds = 300;
            if (taskNode.has("timeout_seconds") && !taskNode.get("timeout_seconds").isNull()) {
                taskTimeoutSeconds = taskNode.get("timeout_seconds").asInt();
                taskTimeoutSeconds = Math.max(30, Math.min(3600, taskTimeoutSeconds));
            }
            
            SubAgentTask subTask = manager.createSubAgent(task, subagentType, taskTimeoutSeconds, dependsOnTaskIds, null);
            launchedTasks.add(subTask);
            indexToTaskPosition.put(taskIndex, launchedTasks.size() - 1);
            taskIndex++;
        }



        if (launchedTasks.isEmpty()) {
            return "❌ 没有有效的任务可以启动";
        }

        if (!waitForAll) {
            return SubAgentResultFormatter.formatBatchResults(launchedTasks);
        }

        for (SubAgentTask task : launchedTasks) {
            try {
                task.awaitCompletion(waitTimeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("等待子Agent完成失败: {}", e.getMessage());
            }
        }

        return SubAgentResultFormatter.formatBatchResults(launchedTasks);
    }
}
