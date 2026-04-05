package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SequentialPlanExecutorTest {

    private SequentialPlanExecutor executor;
    private LlmClient mockLlmClient;
    private ExecutionContext context;

    @BeforeEach
    void setUp() throws LlmException {
        executor = new SequentialPlanExecutor();
        mockLlmClient = mock(LlmClient.class);

        ChatResponse mockResponse = mock(ChatResponse.class);
        when(mockResponse.getContent()).thenReturn("LLM响应内容");
        when(mockLlmClient.chat(any())).thenReturn(mockResponse);

        context = ExecutionContext.builder()
                .llmClient(mockLlmClient)
                .conversationHistory(Collections.emptyList())
                .build();
    }

    private ExecutionPlan createPlan(ExecutionStep... steps) {
        return ExecutionPlan.builder()
                .intent(IntentResult.builder()
                        .type(IntentType.QUESTION)
                        .confidence(0.9)
                        .build())
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .steps(java.util.Arrays.asList(steps))
                .build();
    }

    @Test
    void testExecuteEmptyPlan() {
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(IntentResult.builder().type(IntentType.QUESTION).build())
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .build();

        PlanResult result = executor.execute(plan, context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalStepCount());
    }

    @Test
    void testExecuteSingleLlmStep() {
        ExecutionStep step = ExecutionStep.llmCall("step-1", "生成响应");
        ExecutionPlan plan = createPlan(step);

        PlanResult result = executor.execute(plan, context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalStepCount());
        assertEquals(1, result.getSuccessCount());

        StepResult stepResult = result.getStepResult("step-1");
        assertNotNull(stepResult);
        assertEquals(StepStatus.SUCCESS, stepResult.getStatus());
        assertEquals("LLM响应内容", stepResult.getOutput());
    }

    @Test
    void testExecuteMultipleSteps() {
        ExecutionStep step1 = ExecutionStep.llmCall("step-1", "步骤1");
        ExecutionStep step2 = ExecutionStep.llmCall("step-2", "步骤2");
        ExecutionPlan plan = createPlan(step1, step2);

        PlanResult result = executor.execute(plan, context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalStepCount());
        assertEquals(2, result.getSuccessCount());
    }

    @Test
    void testExecuteStepsWithDependencies() {
        ExecutionStep step1 = ExecutionStep.llmCall("step-1", "步骤1");
        ExecutionStep step2 = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.LLM_CALL)
                .description("步骤2")
                .dependsOn("step-1")
                .build();
        ExecutionPlan plan = createPlan(step1, step2);

        PlanResult result = executor.execute(plan, context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalStepCount());
    }

    @Test
    void testSkipsStepWithUnsatisfiedDependency() {
        ExecutionStep step1 = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.TOOL_CALL)
                .toolName("nonexistent_tool")
                .description("不存在的工具")
                .build();
        
        ExecutionStep step2 = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.LLM_CALL)
                .description("依赖步骤1")
                .dependsOn("step-1")
                .build();

        ExecutionPlan plan = createPlan(step1, step2);

        PlanResult result = executor.execute(plan, context);

        assertNotNull(result);
        StepResult step2Result = result.getStepResult("step-2");
        assertEquals(StepStatus.SKIPPED, step2Result.getStatus());
    }

    @Test
    void testExecuteWaitStep() {
        ExecutionStep step = ExecutionStep.builder()
                .id("wait-1")
                .type(StepType.WAIT)
                .timeoutMs(100)
                .build();
        ExecutionPlan plan = createPlan(step);

        long start = System.currentTimeMillis();
        PlanResult result = executor.execute(plan, context);
        long duration = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(duration >= 100, "应该等待至少100ms");
    }

    @Test
    void testSupportsSequentialStrategy() {
        assertTrue(executor.supports(ExecutionStrategy.SEQUENTIAL));
        assertFalse(executor.supports(ExecutionStrategy.PARALLEL));
        assertFalse(executor.supports(ExecutionStrategy.CONDITIONAL));
    }

    @Test
    void testGetName() {
        assertEquals("SequentialPlanExecutor", executor.getName());
    }

    @Test
    void testRecordsDuration() {
        ExecutionStep step = ExecutionStep.llmCall("step-1", "生成响应");
        ExecutionPlan plan = createPlan(step);

        PlanResult result = executor.execute(plan, context);

        assertTrue(result.getTotalDurationMs() >= 0);

        StepResult stepResult = result.getStepResult("step-1");
        assertTrue(stepResult.getDurationMs() >= 0);
    }

    @Test
    void testGetFailedSteps() {
        ExecutionStep step1 = ExecutionStep.llmCall("step-1", "成功步骤");
        ExecutionStep step2 = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.TOOL_CALL)
                .toolName("nonexistent")
                .description("失败步骤")
                .build();
        ExecutionPlan plan = createPlan(step1, step2);

        PlanResult result = executor.execute(plan, context);

        assertEquals(1, result.getFailedSteps().size());
    }

    @Test
    void testGetSuccessfulSteps() {
        ExecutionStep step1 = ExecutionStep.llmCall("step-1", "步骤1");
        ExecutionStep step2 = ExecutionStep.llmCall("step-2", "步骤2");
        ExecutionPlan plan = createPlan(step1, step2);

        PlanResult result = executor.execute(plan, context);

        assertEquals(2, result.getSuccessfulSteps().size());
    }

    @Test
    void testHandlesNullLlmClient() {
        ExecutionContext nullClientContext = ExecutionContext.builder()
                .llmClient(null)
                .build();

        ExecutionStep step = ExecutionStep.llmCall("step-1", "测试");
        ExecutionPlan plan = createPlan(step);

        PlanResult result = executor.execute(plan, nullClientContext);

        assertNotNull(result);
        StepResult stepResult = result.getStepResult("step-1");
        assertEquals(StepStatus.FAILED, stepResult.getStatus());
    }

    @Test
    void testPlanResultBuilder() {
        PlanResult result = PlanResult.builder()
                .planId("test-plan")
                .success(true)
                .summary("测试摘要")
                .totalDurationMs(1000)
                .build();

        assertEquals("test-plan", result.getPlanId());
        assertTrue(result.isSuccess());
        assertEquals("测试摘要", result.getSummary());
        assertEquals(1000, result.getTotalDurationMs());
    }

    @Test
    void testStepResultStaticFactoryMethods() {
        StepResult success = StepResult.success("step-1", "输出");
        assertEquals(StepStatus.SUCCESS, success.getStatus());
        assertEquals("输出", success.getOutput());

        StepResult failure = StepResult.failure("step-2", "错误");
        assertEquals(StepStatus.FAILED, failure.getStatus());
        assertEquals("错误", failure.getError());

        StepResult skipped = StepResult.skipped("step-3", "原因");
        assertEquals(StepStatus.SKIPPED, skipped.getStatus());
        assertEquals("原因", skipped.getOutput());
    }
}
