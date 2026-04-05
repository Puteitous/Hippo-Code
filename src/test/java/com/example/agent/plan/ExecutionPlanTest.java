package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionPlanTest {

    private IntentResult createTestIntent(IntentType type) {
        return IntentResult.builder()
                .type(type)
                .confidence(0.9)
                .build();
    }

    @Test
    void testBuilderCreatesValidPlan() {
        IntentResult intent = createTestIntent(IntentType.QUESTION);
        
        ExecutionPlan plan = ExecutionPlan.builder()
                .id("plan-1")
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .build();

        assertEquals("plan-1", plan.getId());
        assertEquals(intent, plan.getIntent());
        assertEquals(ExecutionStrategy.SEQUENTIAL, plan.getStrategy());
        assertTrue(plan.getSteps().isEmpty());
        assertTrue(plan.isEmpty());
        assertEquals(0, plan.getStepCount());
    }

    @Test
    void testBuilderWithSteps() {
        IntentResult intent = createTestIntent(IntentType.CODE_GENERATION);
        
        ExecutionStep step1 = ExecutionStep.llmCall("step-1", "分析需求");
        ExecutionStep step2 = ExecutionStep.llmCall("step-2", "生成代码");
        ExecutionStep step3 = ExecutionStep.toolCall("step-3", "write_file", "写入文件");

        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(step1)
                .step(step2)
                .step(step3)
                .build();

        assertEquals(3, plan.getStepCount());
        assertFalse(plan.isEmpty());
        assertEquals(step1, plan.getFirstStep());
        assertEquals(step3, plan.getLastStep());
    }

    @Test
    void testBuilderGeneratesId() {
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .build();

        assertNotNull(plan.getId());
        assertTrue(plan.getId().startsWith("plan-"));
    }

    @Test
    void testStaticFactoryMethods() {
        IntentResult intent = createTestIntent(IntentType.QUESTION);

        ExecutionPlan emptyPlan = ExecutionPlan.empty(intent);
        assertTrue(emptyPlan.isEmpty());
        assertEquals(intent, emptyPlan.getIntent());

        ExecutionStep step = ExecutionStep.llmCall("step-1", "回答问题");
        ExecutionPlan singlePlan = ExecutionPlan.singleStep(intent, step);
        assertEquals(1, singlePlan.getStepCount());
        assertEquals(step, singlePlan.getFirstStep());
    }

    @Test
    void testGetStep() {
        ExecutionStep step1 = ExecutionStep.llmCall("step-1", "步骤1");
        ExecutionStep step2 = ExecutionStep.llmCall("step-2", "步骤2");

        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .step(step1)
                .step(step2)
                .build();

        assertEquals(step1, plan.getStep("step-1"));
        assertEquals(step2, plan.getStep("step-2"));
        assertNull(plan.getStep("nonexistent"));
    }

    @Test
    void testGetIndependentSteps() {
        ExecutionStep step1 = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .build();

        ExecutionStep step2 = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.LLM_CALL)
                .build();

        ExecutionStep step3 = ExecutionStep.builder()
                .id("step-3")
                .type(StepType.LLM_CALL)
                .dependsOn("step-1")
                .build();

        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .step(step1)
                .step(step2)
                .step(step3)
                .build();

        List<ExecutionStep> independentSteps = plan.getIndependentSteps();
        assertEquals(2, independentSteps.size());
        assertTrue(independentSteps.contains(step1));
        assertTrue(independentSteps.contains(step2));
        assertFalse(independentSteps.contains(step3));
    }

    @Test
    void testGetStepsDependingOn() {
        ExecutionStep step1 = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .build();

        ExecutionStep step2 = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.LLM_CALL)
                .dependsOn("step-1")
                .build();

        ExecutionStep step3 = ExecutionStep.builder()
                .id("step-3")
                .type(StepType.LLM_CALL)
                .dependsOn("step-1")
                .build();

        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .step(step1)
                .step(step2)
                .step(step3)
                .build();

        List<ExecutionStep> dependentSteps = plan.getStepsDependingOn("step-1");
        assertEquals(2, dependentSteps.size());
        assertTrue(dependentSteps.contains(step2));
        assertTrue(dependentSteps.contains(step3));
    }

    @Test
    void testGetIntentType() {
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.CODE_GENERATION))
                .build();

        assertEquals(IntentType.CODE_GENERATION, plan.getIntentType());
    }

    @Test
    void testVariables() {
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .variable("file_path", "/path/to/file")
                .variable("max_lines", 100)
                .build();

        assertEquals("/path/to/file", plan.getVariableAsString("file_path"));
        assertEquals(100, plan.getVariable("max_lines", Integer.class));
    }

    @Test
    void testStepsAreImmutable() {
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .step(ExecutionStep.llmCall("step-1", "步骤1"))
                .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            plan.getSteps().add(ExecutionStep.llmCall("step-2", "步骤2"));
        });
    }

    @Test
    void testCreatedAt() {
        long before = System.currentTimeMillis();
        ExecutionPlan plan = ExecutionPlan.builder()
                .intent(createTestIntent(IntentType.QUESTION))
                .build();
        long after = System.currentTimeMillis();

        assertTrue(plan.getCreatedAt() >= before);
        assertTrue(plan.getCreatedAt() <= after);
    }

    @Test
    void testToString() {
        ExecutionPlan plan = ExecutionPlan.builder()
                .id("test-plan")
                .intent(createTestIntent(IntentType.QUESTION))
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(ExecutionStep.llmCall("step-1", "步骤1"))
                .build();

        String str = plan.toString();
        assertTrue(str.contains("test-plan"));
        assertTrue(str.contains("QUESTION"));
        assertTrue(str.contains("stepCount=1"));
        assertTrue(str.contains("SEQUENTIAL"));
    }
}
