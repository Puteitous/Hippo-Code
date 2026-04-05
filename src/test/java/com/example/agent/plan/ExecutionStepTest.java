package com.example.agent.plan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionStepTest {

    @Test
    void testBuilderCreatesValidStep() {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .description("调用LLM生成响应")
                .build();

        assertEquals("step-1", step.getId());
        assertEquals(StepType.LLM_CALL, step.getType());
        assertEquals("调用LLM生成响应", step.getDescription());
        assertNull(step.getToolName());
        assertTrue(step.getArguments().isEmpty());
        assertTrue(step.getDependencies().isEmpty());
    }

    @Test
    void testBuilderWithAllFields() {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.TOOL_CALL)
                .description("读取文件")
                .toolName("read_file")
                .argument("file_path", "/path/to/file")
                .argument("encoding", "UTF-8")
                .dependsOn("step-1")
                .condition("file_exists == true")
                .retryCount(3)
                .timeoutMs(30000)
                .metadata("priority", "high")
                .build();

        assertEquals("step-2", step.getId());
        assertEquals(StepType.TOOL_CALL, step.getType());
        assertEquals("read_file", step.getToolName());
        assertEquals(2, step.getArguments().size());
        assertEquals("/path/to/file", step.getArgumentAsString("file_path"));
        assertEquals("UTF-8", step.getArgumentAsString("encoding"));
        assertEquals(1, step.getDependencies().size());
        assertTrue(step.getDependencies().contains("step-1"));
        assertEquals("file_exists == true", step.getCondition());
        assertEquals(3, step.getRetryCount());
        assertEquals(30000, step.getTimeoutMs());
        assertEquals("high", step.getMetadata().get("priority"));
    }

    @Test
    void testBuilderRequiresId() {
        assertThrows(IllegalArgumentException.class, () -> {
            ExecutionStep.builder().build();
        });
    }

    @Test
    void testStaticFactoryMethods() {
        ExecutionStep llmStep = ExecutionStep.llmCall("llm-1", "生成代码");
        assertEquals("llm-1", llmStep.getId());
        assertEquals(StepType.LLM_CALL, llmStep.getType());
        assertEquals("生成代码", llmStep.getDescription());

        ExecutionStep toolStep = ExecutionStep.toolCall("tool-1", "read_file", "读取文件");
        assertEquals("tool-1", toolStep.getId());
        assertEquals(StepType.TOOL_CALL, toolStep.getType());
        assertEquals("read_file", toolStep.getToolName());
    }

    @Test
    void testHasDependencies() {
        ExecutionStep noDeps = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .build();
        assertFalse(noDeps.hasDependencies());

        ExecutionStep withDeps = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.LLM_CALL)
                .dependsOn("step-1")
                .build();
        assertTrue(withDeps.hasDependencies());
    }

    @Test
    void testHasCondition() {
        ExecutionStep noCondition = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .build();
        assertFalse(noCondition.hasCondition());

        ExecutionStep withCondition = ExecutionStep.builder()
                .id("step-2")
                .type(StepType.CONDITION)
                .condition("x > 0")
                .build();
        assertTrue(withCondition.hasCondition());
    }

    @Test
    void testGetArgumentWithType() {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.TOOL_CALL)
                .argument("string_value", "hello")
                .argument("int_value", 42)
                .argument("bool_value", true)
                .build();

        assertEquals("hello", step.getArgument("string_value", String.class));
        assertEquals(42, step.getArgument("int_value", Integer.class));
        assertEquals(true, step.getArgument("bool_value", Boolean.class));
        assertNull(step.getArgument("nonexistent", String.class));
    }

    @Test
    void testArgumentsAreImmutable() {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .argument("key", "value")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            step.getArguments().put("new_key", "new_value");
        });
    }

    @Test
    void testDependenciesAreImmutable() {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .dependsOn("step-0")
                .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            step.getDependencies().add("step-2");
        });
    }

    @Test
    void testToString() {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-1")
                .type(StepType.LLM_CALL)
                .description("测试步骤")
                .build();

        String str = step.toString();
        assertTrue(str.contains("step-1"));
        assertTrue(str.contains("LLM_CALL"));
        assertTrue(str.contains("测试步骤"));
    }
}
