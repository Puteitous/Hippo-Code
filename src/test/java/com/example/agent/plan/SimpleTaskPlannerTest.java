package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTaskPlannerTest {

    private SimpleTaskPlanner planner;
    private PlanningContext context;

    @BeforeEach
    void setUp() {
        planner = new SimpleTaskPlanner();
        context = PlanningContext.builder()
                .userInput("测试输入")
                .currentRound(1)
                .build();
    }

    private IntentResult createIntent(IntentType type) {
        return IntentResult.builder()
                .type(type)
                .confidence(0.9)
                .build();
    }

    @Test
    void testPlanForQuestion() {
        IntentResult intent = createIntent(IntentType.QUESTION);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertEquals(intent, plan.getIntent());
        assertEquals(ExecutionStrategy.SEQUENTIAL, plan.getStrategy());
        assertEquals(1, plan.getStepCount());

        ExecutionStep step = plan.getFirstStep();
        assertEquals(StepType.LLM_CALL, step.getType());
        assertTrue(step.getDescription().contains("问题"));
    }

    @Test
    void testPlanForCodeGeneration() {
        IntentResult intent = createIntent(IntentType.CODE_GENERATION);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertEquals(1, plan.getStepCount());
        assertEquals(StepType.LLM_CALL, plan.getFirstStep().getType());
    }

    @Test
    void testPlanForCodeModification() {
        IntentResult intent = IntentResult.builder()
                .type(IntentType.CODE_MODIFICATION)
                .confidence(0.9)
                .entity("target_file", "/path/to/file.java")
                .build();

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertTrue(plan.getStepCount() >= 1);

        boolean hasReadStep = plan.getSteps().stream()
                .anyMatch(s -> s.getType() == StepType.TOOL_CALL && 
                              "read_file".equals(s.getToolName()));
        assertTrue(hasReadStep, "应该包含读取文件步骤");
    }

    @Test
    void testPlanForCodeModificationWithoutFile() {
        IntentResult intent = createIntent(IntentType.CODE_MODIFICATION);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertTrue(plan.getStepCount() >= 1);
    }

    @Test
    void testPlanForDebugging() {
        IntentResult intent = createIntent(IntentType.DEBUGGING);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertEquals(1, plan.getStepCount());
        assertEquals(StepType.LLM_CALL, plan.getFirstStep().getType());
    }

    @Test
    void testPlanForFileOperationRead() {
        IntentResult intent = IntentResult.builder()
                .type(IntentType.FILE_OPERATION)
                .confidence(0.9)
                .entity("operation", "read")
                .build();

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertTrue(plan.getStepCount() >= 1);
    }

    @Test
    void testPlanForFileOperationWrite() {
        IntentResult intent = IntentResult.builder()
                .type(IntentType.FILE_OPERATION)
                .confidence(0.9)
                .entity("operation", "write")
                .build();

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertTrue(plan.getStepCount() >= 1);
    }

    @Test
    void testPlanForProjectAnalysis() {
        IntentResult intent = createIntent(IntentType.PROJECT_ANALYSIS);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertEquals(1, plan.getStepCount());
        assertEquals(StepType.LLM_CALL, plan.getFirstStep().getType());
    }

    @Test
    void testPlanForCodeReview() {
        IntentResult intent = createIntent(IntentType.CODE_REVIEW);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertEquals(1, plan.getStepCount());
        assertEquals(StepType.LLM_CALL, plan.getFirstStep().getType());
    }

    @Test
    void testPlanForUnknownIntent() {
        IntentResult intent = createIntent(IntentType.UNKNOWN);
        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertTrue(plan.getStepCount() >= 1);
    }

    @Test
    void testPlanForNullIntent() {
        ExecutionPlan plan = planner.plan(null, context);

        assertNotNull(plan);
        assertTrue(plan.getStepCount() >= 1);
    }

    @Test
    void testSupportsAllIntentTypes() {
        for (IntentType type : IntentType.values()) {
            assertTrue(planner.supports(type), "应该支持意图类型: " + type);
        }
    }

    @Test
    void testIsEnabledByDefault() {
        assertTrue(planner.isEnabled());
    }

    @Test
    void testGetPriority() {
        assertEquals(0, planner.getPriority());
    }

    @Test
    void testGetName() {
        assertEquals("SimpleTaskPlanner", planner.getName());
    }
}
