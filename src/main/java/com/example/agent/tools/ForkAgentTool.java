package com.example.agent.tools;

import com.example.agent.core.di.ServiceLocator;
import com.example.agent.subagent.BuiltInAgent;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentResultFormatter;
import com.example.agent.subagent.SubAgentTask;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ForkAgentTool implements ToolExecutor {
    private SubAgentManager subAgentManager;

    public ForkAgentTool() {
    }

    private SubAgentManager getManager() {
        if (subAgentManager == null) {
            subAgentManager = ServiceLocator.getOrNull(SubAgentManager.class);
        }
        return subAgentManager;
    }

    @Override
    public String getName() {
        return "fork_agent";
    }

    @Override
    public String getDescription() {
        SubAgentManager manager = getManager();
        String agentMenu = manager != null ? manager.getFullAgentMenu() : BuiltInAgent.getAgentMenu();
        
        return "创建一个专家子 Agent 来执行独立的后台任务。这是降低 Token 成本的关键优化。\n\n" +
               agentMenu + "\n" +
               "子 Agent 拥有独立的上下文，可以并行执行代码搜索、分析、验证等任务。" +
               "适用于可以并行化的独立子任务。推荐省略 subagent_type 使用缓存优化模式。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "task": {
                        "type": "string",
                        "description": "子任务的详细描述，明确说明子 Agent 需要完成什么工作"
                    },
                    "subagent_type": {
                        "type": "string",
                        "description": "专家类型。**省略此参数 = 完整复用当前上下文（98% 缓存命中，强烈推荐）**。可选值: explore（代码搜索）, plan（方案设计）, verification（独立验证）, general（通用）"
                    },
                    "wait_for_result": {
                        "type": "boolean",
                        "description": "是否等待执行完成并返回结果。true=阻塞等待并返回完整结果，false=后台异步执行",
                        "default": false
                    },
                    "timeout_seconds": {
                        "type": "integer",
                        "description": "任务超时时间（秒），默认 300 秒（5分钟）。超过时间将强制终止任务",
                        "default": 300,
                        "minimum": 30,
                        "maximum": 3600
                    }
                },
                "required": ["task"]
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

        if (!arguments.has("task") || arguments.get("task").isNull()) {
            throw new ToolExecutionException("缺少必需参数: task");
        }

        String task = arguments.get("task").asText();
        String subagentType = arguments.has("subagent_type") && !arguments.get("subagent_type").isNull()
            ? arguments.get("subagent_type").asText()
            : null;
        boolean waitForResult = arguments.has("wait_for_result") 
            && arguments.get("wait_for_result").asBoolean();
        
        int timeoutSeconds = 300;
        if (arguments.has("timeout_seconds") && !arguments.get("timeout_seconds").isNull()) {
            timeoutSeconds = arguments.get("timeout_seconds").asInt();
            timeoutSeconds = Math.max(30, Math.min(3600, timeoutSeconds));
        }

        SubAgentTask subTask = manager.createSubAgent(task, subagentType, timeoutSeconds, null, null);
        String agentTypeDisplay = subagentType != null ? subagentType : "Fork 优化模式";

        if (!waitForResult) {
            return SubAgentResultFormatter.formatSingleResult(subTask);
        }

        try {
            SubAgentTask completed = subTask.awaitCompletion(120, TimeUnit.SECONDS);
            return SubAgentResultFormatter.formatSingleResult(completed);

        } catch (Exception e) {
            return SubAgentResultFormatter.formatSingleResult(subTask);
        }
    }
}
