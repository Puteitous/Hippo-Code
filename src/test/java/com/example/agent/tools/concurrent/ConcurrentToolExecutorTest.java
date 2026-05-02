package com.example.agent.tools.concurrent;

import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.tools.ToolExecutionException;
import com.example.agent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentToolExecutorTest {

    private ToolRegistry toolRegistry;
    private ConcurrentToolExecutor executor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        executor = new ConcurrentToolExecutor(toolRegistry);
    }

    private ToolCall createToolCall(String id, String name, String arguments) {
        ToolCall toolCall = new ToolCall();
        toolCall.setId(id);
        toolCall.setFunction(new FunctionCall(name, arguments));
        return toolCall;
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("null工具调用列表返回空结果")
        void testNullToolCalls() {
            List<ToolExecutionResult> results = executor.executeConcurrently(null);
            
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("空工具调用列表返回空结果")
        void testEmptyToolCalls() {
            List<ToolExecutionResult> results = executor.executeConcurrently(new ArrayList<>());
            
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("单个工具调用不使用并发")
        void testSingleToolCallNotConcurrent() throws ToolExecutionException {
            AtomicInteger executionCount = new AtomicInteger(0);
            toolRegistry.register(new MockToolExecutor("single_tool", (args) -> {
                executionCount.incrementAndGet();
                return "result";
            }));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "single_tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertEquals(1, executionCount.get());
        }
    }

    @Nested
    @DisplayName("工具不存在测试")
    class ToolNotFoundTests {

        @Test
        @DisplayName("工具不存在返回失败结果，不崩溃")
        void testToolNotExist() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "nonexistent_tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
            assertTrue(results.get(0).getErrorMessage().contains("未知的工具"));
        }

        @Test
        @DisplayName("工具名称为null返回失败结果")
        void testNullToolName() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", null, "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
            assertTrue(results.get(0).getErrorMessage().contains("工具名称为空"));
        }

        @Test
        @DisplayName("工具名称为空字符串返回失败结果")
        void testEmptyToolName() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
        }

        @Test
        @DisplayName("Function为null返回失败结果")
        void testNullFunction() {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(null);

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(toolCall);

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
        }
    }

    @Nested
    @DisplayName("并发执行测试")
    class ConcurrentExecutionTests {

        @Test
        @DisplayName("多个工具调用并发执行")
        void testMultipleToolCallsConcurrent() throws Exception {
            CountDownLatch latch = new CountDownLatch(3);
            AtomicInteger completedCount = new AtomicInteger(0);

            toolRegistry.register(new MockToolExecutor("tool_a", (args) -> {
                latch.countDown();
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return completedCount.incrementAndGet() == 3 ? "last" : "result_a";
            }));
            toolRegistry.register(new MockToolExecutor("tool_b", (args) -> {
                latch.countDown();
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return completedCount.incrementAndGet() == 3 ? "last" : "result_b";
            }));
            toolRegistry.register(new MockToolExecutor("tool_c", (args) -> {
                latch.countDown();
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return completedCount.incrementAndGet() == 3 ? "last" : "result_c";
            }));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "tool_a", "{}"));
            toolCalls.add(createToolCall("call-2", "tool_b", "{}"));
            toolCalls.add(createToolCall("call-3", "tool_c", "{}"));

            long startTime = System.currentTimeMillis();
            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
            long duration = System.currentTimeMillis() - startTime;

            assertEquals(3, results.size());
            assertTrue(duration < 2000, "并发执行应该更快完成");
        }

        @Test
        @DisplayName("结果按原始顺序返回")
        void testResultsInOrder() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("tool", "result"));

            List<ToolCall> toolCalls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                toolCalls.add(createToolCall("call-" + i, "tool", "{\"index\": " + i + "}"));
            }

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(10, results.size());
            for (int i = 0; i < 10; i++) {
                assertEquals(i, results.get(i).getIndex());
            }
        }
    }

    @Nested
    @DisplayName("参数解析测试")
    class ArgumentParsingTests {

        @Test
        @DisplayName("无效JSON参数返回失败结果")
        void testInvalidJsonArguments() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", "not valid json"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
            assertTrue(results.get(0).getErrorMessage().contains("参数解析失败"));
        }

        @Test
        @DisplayName("null参数返回失败结果")
        void testNullArguments() {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new FunctionCall("test_tool", null));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(toolCall);

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
        }

        @Test
        @DisplayName("复杂JSON参数正确解析")
        void testComplexJsonArguments() throws ToolExecutionException {
            StringBuilder capturedArgs = new StringBuilder();
            toolRegistry.register(new MockToolExecutor("test_tool", (args) -> {
                capturedArgs.append(args.toString());
                return "success";
            }));

            String complexJson = "{\"nested\": {\"key\": \"value\"}, \"array\": [1, 2, 3]}";
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", complexJson));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertTrue(results.get(0).isSuccess());
        }
    }

    @Nested
    @DisplayName("执行统计测试")
    class ExecutionStatsTests {

        @Test
        @DisplayName("空结果统计")
        void testEmptyResultsStats() {
            ConcurrentToolExecutor.ExecutionStats stats = executor.getExecutionStats(null);

            assertEquals(0, stats.getTotalCount());
            assertEquals(0, stats.getSuccessCount());
            assertEquals(0, stats.getFailureCount());
            assertEquals(0, stats.getTotalExecutionTimeMs());
            assertEquals(0, stats.getAverageExecutionTimeMs());
        }

        @Test
        @DisplayName("全部成功统计")
        void testAllSuccessStats() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("tool", "result"));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "tool", "{}"));
            toolCalls.add(createToolCall("call-2", "tool", "{}"));
            toolCalls.add(createToolCall("call-3", "tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
            ConcurrentToolExecutor.ExecutionStats stats = executor.getExecutionStats(results);

            assertEquals(3, stats.getTotalCount());
            assertEquals(3, stats.getSuccessCount());
            assertEquals(0, stats.getFailureCount());
        }

        @Test
        @DisplayName("部分成功统计")
        void testPartialSuccessStats() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("existing_tool", "result"));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "existing_tool", "{}"));
            toolCalls.add(createToolCall("call-2", "nonexistent_tool", "{}"));
            toolCalls.add(createToolCall("call-3", "existing_tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);
            ConcurrentToolExecutor.ExecutionStats stats = executor.getExecutionStats(results);

            assertEquals(3, stats.getTotalCount());
            assertEquals(2, stats.getSuccessCount());
            assertEquals(1, stats.getFailureCount());
        }
    }

    @Nested
    @DisplayName("执行时间测试")
    class ExecutionTimeTests {

        @Test
        @DisplayName("记录执行时间")
        void testExecutionTimeRecorded() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("tool", (args) -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "result";
            }));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertTrue(results.get(0).getExecutionTimeMs() >= 50);
        }

        @Test
        @DisplayName("失败执行也记录时间")
        void testExecutionTimeRecordedOnFailure() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "nonexistent", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertTrue(results.get(0).getExecutionTimeMs() >= 0);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("工具抛出ToolExecutionException")
        void testToolExecutionException() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("failing_tool", (args) -> {
                throw new ToolExecutionException("Tool failed intentionally");
            }));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "failing_tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
            assertEquals("Tool failed intentionally", results.get(0).getErrorMessage());
        }

        @Test
        @DisplayName("工具抛出运行时异常")
        void testRuntimeException() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("crashing_tool", (args) -> {
                throw new RuntimeException("Unexpected error");
            }));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "crashing_tool", "{}"));

            List<ToolExecutionResult> results = executor.executeConcurrently(toolCalls);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isSuccess());
        }
    }

    @Nested
    @DisplayName("ToolExecutionResult测试")
    class ToolExecutionResultTests {

        @Test
        @DisplayName("Builder构建成功结果")
        void testBuilderSuccess() {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .index(1)
                    .toolCallId("call-123")
                    .toolName("test_tool")
                    .result("success result")
                    .success(true)
                    .executionTimeMs(100)
                    .build();

            assertEquals(1, result.getIndex());
            assertEquals("call-123", result.getToolCallId());
            assertEquals("test_tool", result.getToolName());
            assertEquals("success result", result.getResult());
            assertTrue(result.isSuccess());
            assertNull(result.getErrorMessage());
            assertEquals(100, result.getExecutionTimeMs());
        }

        @Test
        @DisplayName("Builder构建失败结果")
        void testBuilderFailure() {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .index(0)
                    .toolCallId("call-456")
                    .toolName("failed_tool")
                    .success(false)
                    .errorMessage("Error occurred")
                    .executionTimeMs(50)
                    .build();

            assertFalse(result.isSuccess());
            assertEquals("Error occurred", result.getErrorMessage());
            assertNull(result.getResult());
        }

        @Test
        @DisplayName("默认success为true")
        void testBuilderDefaultSuccess() {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .toolCallId("call-789")
                    .build();

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("toString包含关键信息")
        void testToString() {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .index(1)
                    .toolCallId("call-123")
                    .toolName("test_tool")
                    .success(true)
                    .executionTimeMs(100)
                    .build();

            String str = result.toString();

            assertTrue(str.contains("index=1"));
            assertTrue(str.contains("toolCallId='call-123'"));
            assertTrue(str.contains("toolName='test_tool'"));
            assertTrue(str.contains("success=true"));
        }
    }

    @Nested
    @DisplayName("中断处理测试")
    class InterruptHandlingTests {

        @Test
        @DisplayName("工具执行期间响应中断")
        void testToolExecutionRespondsToInterrupt() throws Exception {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch interruptLatch = new CountDownLatch(1);

            toolRegistry.register(new MockToolExecutor("slow_tool", (args) -> {
                startLatch.countDown();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interruptLatch.countDown();
                    throw new ToolExecutionException("工具执行被中断");
                }
                return "should not reach here";
            }));

            toolRegistry.register(new MockToolExecutor("fast_tool", "fast_result"));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "slow_tool", "{}"));
            toolCalls.add(createToolCall("call-2", "fast_tool", "{}"));

            Thread executorThread = new Thread(() -> {
                executor.executeConcurrently(toolCalls);
            });
            executorThread.start();

            assertTrue(startLatch.await(2, TimeUnit.SECONDS), "工具应该开始执行");

            executorThread.interrupt();

            assertTrue(interruptLatch.await(2, TimeUnit.SECONDS), "工具应该响应中断");

            executorThread.join(2000);
            assertFalse(executorThread.isAlive(), "执行线程应该已结束");
        }

        @Test
        @DisplayName("单个后台工具执行响应中断")
        void testSingleBackgroundToolInterrupt() throws Exception {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch interruptLatch = new CountDownLatch(1);

            toolRegistry.register(new MockToolExecutor("slow_tool", (args) -> {
                startLatch.countDown();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interruptLatch.countDown();
                    throw new ToolExecutionException("工具执行被中断");
                }
                return "should not reach here";
            }));

            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "slow_tool", "{}"));

            Thread executorThread = new Thread(() -> {
                executor.executeConcurrently(toolCalls);
            });
            executorThread.start();

            assertTrue(startLatch.await(2, TimeUnit.SECONDS), "工具应该开始执行");

            executorThread.interrupt();

            assertTrue(interruptLatch.await(2, TimeUnit.SECONDS), "工具应该响应中断");

            executorThread.join(2000);
            assertFalse(executorThread.isAlive(), "执行线程应该已结束");
        }
    }

    private interface ToolExecutorFunction {
        String execute(JsonNode args) throws ToolExecutionException;
    }

    private static class MockToolExecutor implements com.example.agent.tools.ToolExecutor {
        private final String name;
        private final ToolExecutorFunction executor;

        MockToolExecutor(String name, String result) {
            this.name = name;
            this.executor = (args) -> result;
        }

        MockToolExecutor(String name, ToolExecutorFunction executor) {
            this.name = name;
            this.executor = executor;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Mock tool for testing";
        }

        @Override
        public String getParametersSchema() {
            return "{\"type\": \"object\"}";
        }

        @Override
        public String execute(JsonNode arguments) throws ToolExecutionException {
            return executor.execute(arguments);
        }
    }
}
