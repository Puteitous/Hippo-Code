package com.example.agent.tools.concurrent;

import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.tools.ToolExecutionException;
import com.example.agent.tools.ToolExecutor;
import com.example.agent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@DisplayName("并发工具执行器测试")
class ConcurrentToolExecutorTest {

    private ToolRegistry toolRegistry;
    private ConcurrentToolExecutor executor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        executor = new ConcurrentToolExecutor(toolRegistry);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("测试 - 空工具列表执行")
    void testEmptyToolCalls() {
        List<ToolCall> toolCalls = new ArrayList<>();
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("测试 - 单个工具调用执行")
    void testSingleToolCall() {
        toolRegistry.register(new MockToolExecutor("test_tool", "测试结果"));
        
        List<ToolCall> toolCalls = List.of(
            createToolCall("call_1", "test_tool", "{}")
        );
        
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("测试结果", results.get(0).getResult());
        assertEquals("test_tool", results.get(0).getToolName());
    }

    @Test
    @DisplayName("测试 - 多个工具并发执行")
    void testMultipleToolCalls() {
        toolRegistry.register(new MockToolExecutor("tool_a", "结果A", 100));
        toolRegistry.register(new MockToolExecutor("tool_b", "结果B", 100));
        toolRegistry.register(new MockToolExecutor("tool_c", "结果C", 100));
        
        List<ToolCall> toolCalls = List.of(
            createToolCall("call_1", "tool_a", "{}"),
            createToolCall("call_2", "tool_b", "{}"),
            createToolCall("call_3", "tool_c", "{}")
        );
        
        long startTime = System.currentTimeMillis();
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        long totalTime = System.currentTimeMillis() - startTime;
        
        assertEquals(3, results.size());
        
        assertTrue(totalTime < 350, "并发执行时间应小于串行时间(300ms)，实际: " + totalTime + "ms");
        
        for (int i = 0; i < results.size(); i++) {
            assertEquals(i, results.get(i).getIndex(), "结果应按原始顺序返回");
        }
    }

    @Test
    @DisplayName("测试 - 结果顺序保持正确")
    void testResultOrderPreserved() {
        toolRegistry.register(new MockToolExecutor("fast_tool", "快速结果", 10));
        toolRegistry.register(new MockToolExecutor("slow_tool", "慢速结果", 200));
        toolRegistry.register(new MockToolExecutor("medium_tool", "中速结果", 50));
        
        List<ToolCall> toolCalls = List.of(
            createToolCall("call_1", "slow_tool", "{}"),
            createToolCall("call_2", "fast_tool", "{}"),
            createToolCall("call_3", "medium_tool", "{}")
        );
        
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        
        assertEquals(3, results.size());
        assertEquals(0, results.get(0).getIndex());
        assertEquals(1, results.get(1).getIndex());
        assertEquals(2, results.get(2).getIndex());
        
        assertEquals("slow_tool", results.get(0).getToolName());
        assertEquals("fast_tool", results.get(1).getToolName());
        assertEquals("medium_tool", results.get(2).getToolName());
    }

    @Test
    @DisplayName("测试 - 工具执行失败处理")
    void testToolExecutionFailure() {
        toolRegistry.register(new MockToolExecutor("success_tool", "成功"));
        toolRegistry.register(new FailingToolExecutor("fail_tool", "模拟失败"));
        toolRegistry.register(new MockToolExecutor("another_success", "另一个成功"));
        
        List<ToolCall> toolCalls = List.of(
            createToolCall("call_1", "success_tool", "{}"),
            createToolCall("call_2", "fail_tool", "{}"),
            createToolCall("call_3", "another_success", "{}")
        );
        
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        
        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertEquals("模拟失败", results.get(1).getErrorMessage());
        assertTrue(results.get(2).isSuccess());
    }

    @Test
    @DisplayName("测试 - 执行统计信息")
    void testExecutionStats() {
        toolRegistry.register(new MockToolExecutor("tool_1", "结果1"));
        toolRegistry.register(new MockToolExecutor("tool_2", "结果2"));
        
        List<ToolCall> toolCalls = List.of(
            createToolCall("call_1", "tool_1", "{}"),
            createToolCall("call_2", "tool_2", "{}")
        );
        
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        ConcurrentToolExecutor.ExecutionStats stats = executor.getExecutionStats(results);
        
        assertEquals(2, stats.getTotalCount());
        assertEquals(2, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
        assertTrue(stats.getTotalExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("测试 - 并发执行时间明显短于串行")
    void testConcurrentExecutionFasterThanSequential() {
        int toolCount = 5;
        int delayPerTool = 100;
        
        for (int i = 0; i < toolCount; i++) {
            toolRegistry.register(new MockToolExecutor("tool_" + i, "结果" + i, delayPerTool));
        }
        
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            toolCalls.add(createToolCall("call_" + i, "tool_" + i, "{}"));
        }
        
        long startTime = System.currentTimeMillis();
        List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
        long concurrentTime = System.currentTimeMillis() - startTime;
        
        long theoreticalSequentialTime = toolCount * delayPerTool;
        
        assertEquals(toolCount, results.size());
        assertTrue(concurrentTime < theoreticalSequentialTime, 
            String.format("并发时间(%dms)应小于理论串行时间(%dms)", 
                concurrentTime, theoreticalSequentialTime));
        
        System.out.printf("并发执行 %d 个工具: %dms (理论串行: %dms)%n", 
            toolCount, concurrentTime, theoreticalSequentialTime);
    }

    @Test
    @DisplayName("测试 - ToolExecutionResult Builder")
    void testToolExecutionResultBuilder() {
        ToolExecutionResult result = ToolExecutionResult.builder()
            .index(5)
            .toolCallId("test_id")
            .toolName("test_tool")
            .result("测试结果")
            .success(true)
            .executionTimeMs(123)
            .build();
        
        assertEquals(5, result.getIndex());
        assertEquals("test_id", result.getToolCallId());
        assertEquals("test_tool", result.getToolName());
        assertEquals("测试结果", result.getResult());
        assertTrue(result.isSuccess());
        assertEquals(123, result.getExecutionTimeMs());
    }

    @Test
    @DisplayName("测试 - FileLockManager 单例")
    void testFileLockManagerSingleton() {
        FileLockManager instance1 = FileLockManager.getInstance();
        FileLockManager instance2 = FileLockManager.getInstance();
        
        assertSame(instance1, instance2, "FileLockManager 应该是单例");
    }

    @Test
    @DisplayName("测试 - FileLockManager 文件锁")
    void testFileLockManagerLock() {
        FileLockManager lockManager = FileLockManager.getInstance();
        String testPath = "/test/path/file.txt";
        
        String result = lockManager.withWriteLock(testPath, () -> "锁定执行成功");
        
        assertEquals("锁定执行成功", result);
        assertFalse(lockManager.isLocked(testPath), "锁应该已释放");
    }

    private ToolCall createToolCall(String id, String toolName, String arguments) {
        ToolCall toolCall = new ToolCall();
        toolCall.setId(id);
        FunctionCall functionCall = new FunctionCall();
        functionCall.setName(toolName);
        functionCall.setArguments(arguments);
        toolCall.setFunction(functionCall);
        return toolCall;
    }

    private static class MockToolExecutor implements ToolExecutor {
        private final String name;
        private final String result;
        private final int delayMs;

        MockToolExecutor(String name, String result) {
            this(name, result, 0);
        }

        MockToolExecutor(String name, String result, int delayMs) {
            this.name = name;
            this.result = result;
            this.delayMs = delayMs;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "模拟工具: " + name;
        }

        @Override
        public String getParametersSchema() {
            return "{\"type\":\"object\"}";
        }

        @Override
        public String execute(JsonNode arguments) throws ToolExecutionException {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        }
    }

    private static class FailingToolExecutor implements ToolExecutor {
        private final String name;
        private final String errorMessage;

        FailingToolExecutor(String name, String errorMessage) {
            this.name = name;
            this.errorMessage = errorMessage;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "失败工具: " + name;
        }

        @Override
        public String getParametersSchema() {
            return "{\"type\":\"object\"}";
        }

        @Override
        public String execute(JsonNode arguments) throws ToolExecutionException {
            throw new ToolExecutionException(errorMessage);
        }
    }
}
