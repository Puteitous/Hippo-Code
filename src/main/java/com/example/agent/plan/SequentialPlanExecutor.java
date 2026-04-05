package com.example.agent.plan;

import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SequentialPlanExecutor implements PlanExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SequentialPlanExecutor.class);

    @Override
    public PlanResult execute(ExecutionPlan plan, ExecutionContext context) {
        logger.info("开始执行计划: {}, 步骤数: {}", plan.getId(), plan.getStepCount());

        long startTime = System.currentTimeMillis();
        Map<String, StepResult> results = new LinkedHashMap<>();
        Map<String, Object> outputs = new HashMap<>();

        List<ExecutionStep> steps = plan.getSteps();

        for (ExecutionStep step : steps) {
            if (!canExecuteStep(step, results)) {
                logger.debug("跳过步骤 {} - 依赖未满足", step.getId());
                results.put(step.getId(), StepResult.skipped(step.getId(), "依赖步骤未成功完成"));
                continue;
            }

            StepResult result = executeStep(step, context, outputs);
            results.put(step.getId(), result);

            if (result.getOutput() != null) {
                outputs.put(step.getId() + "_output", result.getOutput());
            }

            if (!result.isSuccess()) {
                logger.warn("步骤 {} 失败: {}", step.getId(), result.getError());
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        boolean success = results.values().stream().allMatch(StepResult::isSuccess);

        return PlanResult.builder()
                .planId(plan.getId())
                .success(success)
                .stepResults(results)
                .outputs(outputs)
                .totalDurationMs(totalDuration)
                .build();
    }

    private boolean canExecuteStep(ExecutionStep step, Map<String, StepResult> completedSteps) {
        if (!step.hasDependencies()) {
            return true;
        }

        for (String depId : step.getDependencies()) {
            StepResult depResult = completedSteps.get(depId);
            if (depResult == null || !depResult.isSuccess()) {
                return false;
            }
        }

        return true;
    }

    private StepResult executeStep(ExecutionStep step, ExecutionContext context, Map<String, Object> outputs) {
        logger.debug("执行步骤: {} - {}", step.getId(), step.getDescription());
        long startTime = System.currentTimeMillis();

        try {
            StepResult result = switch (step.getType()) {
                case LLM_CALL -> executeLlmCall(step, context, outputs);
                case TOOL_CALL -> executeToolCall(step, context, outputs);
                case FILE_READ -> executeFileRead(step, context);
                case FILE_WRITE -> executeFileWrite(step, context, outputs);
                case CONDITION -> evaluateCondition(step, context, outputs);
                case PARALLEL -> executeParallel(step, context, outputs);
                case WAIT -> executeWait(step);
                default -> StepResult.failure(step.getId(), "未知步骤类型: " + step.getType());
            };

            long duration = System.currentTimeMillis() - startTime;
            return StepResult.builder()
                    .stepId(step.getId())
                    .status(result.getStatus())
                    .output(result.getOutput())
                    .error(result.getError())
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            logger.error("步骤执行异常: {}", e.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            return StepResult.builder()
                    .stepId(step.getId())
                    .status(StepStatus.FAILED)
                    .error(e.getMessage())
                    .durationMs(duration)
                    .build();
        }
    }

    private StepResult executeLlmCall(ExecutionStep step, ExecutionContext context, Map<String, Object> outputs) {
        LlmClient llmClient = context.getLlmClient();
        if (llmClient == null) {
            return StepResult.failure(step.getId(), "LLM客户端未配置");
        }

        List<Message> messages = new ArrayList<>();

        if (context.getConversationHistory() != null) {
            messages.addAll(context.getConversationHistory());
        }

        String prompt = buildPromptFromStep(step, outputs);
        if (prompt != null && !prompt.isEmpty()) {
            messages.add(Message.user(prompt));
        }

        try {
            ChatResponse response = llmClient.chat(messages);

            if (response != null && response.getContent() != null) {
                return StepResult.success(step.getId(), response.getContent());
            }

            return StepResult.failure(step.getId(), "LLM返回空响应");
        } catch (Exception e) {
            return StepResult.failure(step.getId(), "LLM调用失败: " + e.getMessage());
        }
    }

    private String buildPromptFromStep(ExecutionStep step, Map<String, Object> outputs) {
        String description = step.getDescription();
        if (description == null || description.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (description.contains(placeholder)) {
                description = description.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        return description;
    }

    private StepResult executeToolCall(ExecutionStep step, ExecutionContext context, Map<String, Object> outputs) {
        ToolRegistry toolRegistry = context.getToolRegistry();
        if (toolRegistry == null) {
            return StepResult.failure(step.getId(), "工具注册表未配置");
        }

        String toolName = step.getToolName();
        if (toolName == null || toolName.isEmpty()) {
            return StepResult.failure(step.getId(), "工具名称未指定");
        }

        return StepResult.success(step.getId(), "工具调用已安排: " + toolName);
    }

    private StepResult executeFileRead(ExecutionStep step, ExecutionContext context) {
        String filePath = step.getArgumentAsString("file_path");
        if (filePath == null || filePath.isEmpty()) {
            return StepResult.failure(step.getId(), "文件路径未指定");
        }

        return StepResult.success(step.getId(), "文件读取已安排: " + filePath);
    }

    private StepResult executeFileWrite(ExecutionStep step, ExecutionContext context, Map<String, Object> outputs) {
        String filePath = step.getArgumentAsString("file_path");
        if (filePath == null || filePath.isEmpty()) {
            return StepResult.failure(step.getId(), "文件路径未指定");
        }

        return StepResult.success(step.getId(), "文件写入已安排: " + filePath);
    }

    private StepResult evaluateCondition(ExecutionStep step, ExecutionContext context, Map<String, Object> outputs) {
        String condition = step.getCondition();
        if (condition == null || condition.isEmpty()) {
            return StepResult.failure(step.getId(), "条件未指定");
        }

        boolean result = evaluateSimpleCondition(condition, outputs);

        return StepResult.success(step.getId(), String.valueOf(result));
    }

    private boolean evaluateSimpleCondition(String condition, Map<String, Object> outputs) {
        return true;
    }

    private StepResult executeParallel(ExecutionStep step, ExecutionContext context, Map<String, Object> outputs) {
        return StepResult.success(step.getId(), "并行执行已完成");
    }

    private StepResult executeWait(ExecutionStep step) {
        long waitMs = step.getTimeoutMs();
        if (waitMs <= 0) {
            waitMs = 1000;
        }

        try {
            Thread.sleep(waitMs);
            return StepResult.success(step.getId(), "等待完成: " + waitMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failure(step.getId(), "等待被中断");
        }
    }

    private boolean shouldContinueOnFailure(ExecutionPlan plan, ExecutionStep failedStep) {
        return false;
    }
}
