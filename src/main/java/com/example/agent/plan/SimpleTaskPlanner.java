package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTaskPlanner implements TaskPlanner {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTaskPlanner.class);

    @Override
    public ExecutionPlan plan(IntentResult intent, PlanningContext context) {
        logger.debug("规划意图: {}", intent);

        if (intent == null || intent.getType() == IntentType.UNKNOWN) {
            return createDefaultPlan(context);
        }

        return switch (intent.getType()) {
            case QUESTION -> createQuestionPlan(intent, context);
            case CODE_GENERATION -> createCodeGenerationPlan(intent, context);
            case CODE_MODIFICATION -> createCodeModificationPlan(intent, context);
            case DEBUGGING -> createDebuggingPlan(intent, context);
            case FILE_OPERATION -> createFileOperationPlan(intent, context);
            case PROJECT_ANALYSIS -> createProjectAnalysisPlan(intent, context);
            case CODE_REVIEW -> createCodeReviewPlan(intent, context);
            default -> createDefaultPlan(context);
        };
    }

    private ExecutionPlan createQuestionPlan(IntentResult intent, PlanningContext context) {
        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-llm-question")
                .type(StepType.LLM_CALL)
                .description("回答用户问题")
                .build();

        return ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(llmStep)
                .build();
    }

    private ExecutionPlan createCodeGenerationPlan(IntentResult intent, PlanningContext context) {
        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-code-gen")
                .type(StepType.LLM_CALL)
                .description("生成代码")
                .build();

        return ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(llmStep)
                .build();
    }

    private ExecutionPlan createCodeModificationPlan(IntentResult intent, PlanningContext context) {
        String targetFile = intent.getEntityAsString("target_file");

        ExecutionPlan.Builder planBuilder = ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL);

        if (targetFile != null && !targetFile.isEmpty()) {
            ExecutionStep readStep = ExecutionStep.builder()
                    .id("step-read-file")
                    .type(StepType.TOOL_CALL)
                    .toolName("read_file")
                    .description("读取目标文件: " + targetFile)
                    .argument("file_path", targetFile)
                    .build();
            planBuilder.step(readStep);
        }

        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-modify-code")
                .type(StepType.LLM_CALL)
                .description("分析和修改代码")
                .build();

        if (targetFile != null && !targetFile.isEmpty()) {
            llmStep = ExecutionStep.builder()
                    .id("step-modify-code")
                    .type(StepType.LLM_CALL)
                    .description("分析和修改代码")
                    .dependsOn("step-read-file")
                    .build();
        }

        planBuilder.step(llmStep);

        return planBuilder.build();
    }

    private ExecutionPlan createDebuggingPlan(IntentResult intent, PlanningContext context) {
        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-debug")
                .type(StepType.LLM_CALL)
                .description("分析问题并提供调试建议")
                .build();

        return ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(llmStep)
                .build();
    }

    private ExecutionPlan createFileOperationPlan(IntentResult intent, PlanningContext context) {
        String operation = intent.getEntityAsString("operation");
        String targetFile = intent.getEntityAsString("target_file");

        ExecutionPlan.Builder planBuilder = ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL);

        if ("read".equalsIgnoreCase(operation) || "查看".equals(operation) || "读取".equals(operation)) {
            ExecutionStep readStep = ExecutionStep.builder()
                    .id("step-read-file")
                    .type(StepType.TOOL_CALL)
                    .toolName("read_file")
                    .description("读取文件")
                    .build();
            planBuilder.step(readStep);
        } else if ("write".equalsIgnoreCase(operation) || "写入".equals(operation) || "保存".equals(operation)) {
            ExecutionStep llmStep = ExecutionStep.builder()
                    .id("step-prepare-content")
                    .type(StepType.LLM_CALL)
                    .description("准备写入内容")
                    .build();
            planBuilder.step(llmStep);
        } else {
            ExecutionStep llmStep = ExecutionStep.builder()
                    .id("step-file-op")
                    .type(StepType.LLM_CALL)
                    .description("执行文件操作")
                    .build();
            planBuilder.step(llmStep);
        }

        return planBuilder.build();
    }

    private ExecutionPlan createProjectAnalysisPlan(IntentResult intent, PlanningContext context) {
        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-analyze")
                .type(StepType.LLM_CALL)
                .description("分析项目结构")
                .build();

        return ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(llmStep)
                .build();
    }

    private ExecutionPlan createCodeReviewPlan(IntentResult intent, PlanningContext context) {
        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-review")
                .type(StepType.LLM_CALL)
                .description("审查代码")
                .build();

        return ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(llmStep)
                .build();
    }

    private ExecutionPlan createDefaultPlan(PlanningContext context) {
        ExecutionStep llmStep = ExecutionStep.builder()
                .id("step-default")
                .type(StepType.LLM_CALL)
                .description("处理用户请求")
                .build();

        return ExecutionPlan.builder()
                .intent(IntentResult.unknown())
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(llmStep)
                .build();
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
