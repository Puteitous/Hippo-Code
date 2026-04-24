package com.example.agent.tools;

import com.example.agent.core.di.ServiceLocator;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentTask;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;

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
        return "创建一个子 Agent 来执行独立的后台任务。子 Agent 拥有独立的上下文，" +
               "可以并行执行代码搜索、文件读取等任务。适用于可以并行化的独立子任务。" +
               "子 Agent 只能使用文件读取和代码搜索类工具，不能修改文件或执行命令。";
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
                    "system_prompt": {
                        "type": "string",
                        "description": "可选的自定义系统提示词，用于指导子 Agent 的行为"
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
    public String execute(JsonNode arguments) throws ToolExecutionException {
        SubAgentManager manager = getManager();
        if (manager == null) {
            throw new ToolExecutionException("SubAgentManager 未初始化");
        }

        if (!arguments.has("task") || arguments.get("task").isNull()) {
            throw new ToolExecutionException("缺少必需参数: task");
        }

        String task = arguments.get("task").asText();
        String systemPrompt = arguments.has("system_prompt") && !arguments.get("system_prompt").isNull()
            ? arguments.get("system_prompt").asText()
            : null;

        SubAgentTask subTask = manager.forkAgent(task, systemPrompt);

        return "✅ Sub-Agent 已启动\n" +
               "Task ID: " + subTask.getTaskId() + "\n" +
               "任务: " + task + "\n" +
               "状态: 后台执行中...\n\n" +
               "子 Agent 将在后台独立执行任务，完成后会在控制台通知结果。" +
               "你可以继续当前的工作，不需要等待子任务完成。";
    }
}
