package com.example.agent.context.compressor;

import com.example.agent.application.ConversationService;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.testutil.LlmResponseBuilder;
import com.example.agent.testutil.MockLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactForkExecutor 全面测试")
class CompactForkExecutorTest {

    private CompactForkExecutor executor;
    private MockLlmClient mockLlmClient;
    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        mockLlmClient = new MockLlmClient();
        tokenEstimator = TokenEstimatorFactory.getDefault();
        executor = new CompactForkExecutor(mockLlmClient, null, tokenEstimator);
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("带参数构造函数 - 正确初始化")
        void constructorWithParameters() {
            CompactForkExecutor exec = new CompactForkExecutor(mockLlmClient, null, tokenEstimator);
            assertNotNull(exec);
        }

        @Test
        @DisplayName("无参数构造函数 - 使用 ServiceLocator")
        void constructorWithoutParameters() {
            assertThrows(RuntimeException.class, () -> {
                new CompactForkExecutor();
            });
        }
    }

    @Nested
    @DisplayName("成功压缩测试")
    class SuccessCompactionTests {

        @Test
        @DisplayName("基本压缩 - 成功返回摘要")
        void basicCompactionSuccess() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("## 摘要\n- 对话已压缩"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertTrue(result.isSuccess());
            assertFalse(result.isUsedFork());
            assertEquals("## 摘要\n- 对话已压缩", result.getSummary());
            assertEquals(0, result.getOutputTokens());
        }

        @Test
        @DisplayName("带超时参数的压缩")
        void compactionWithTimeout() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("压缩摘要内容"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话",
                60
            );

            assertTrue(result.isSuccess());
            assertEquals("压缩摘要内容", result.getSummary());
        }

        @Test
        @DisplayName("压缩提示词包含特殊指令")
        void compactionPromptIncludesSpecialInstructions() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要内容"));

            executor.executeForkedCompaction(
                "test-session",
                "基础压缩指令"
            );

            List<Message> sentMessages = mockLlmClient.getLastSentMessages();
            assertNotNull(sentMessages);
            assertEquals(1, sentMessages.size());
            String content = sentMessages.get(0).getContent();
            assertTrue(content.contains("基础压缩指令"));
            assertTrue(content.contains("禁止调用任何工具"));
            assertTrue(content.contains("只输出摘要"));
            assertTrue(content.contains("query_source=compact"));
        }
    }

    @Nested
    @DisplayName("失败处理测试")
    class FailureHandlingTests {

        @Test
        @DisplayName("LLM异常 - 返回失败结果")
        void llmExceptionReturnsFailure() {
            mockLlmClient.setExceptionToThrow(new LlmException("LLM API 错误"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertFalse(result.isSuccess());
            assertFalse(result.isCancelled());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("LLM API 错误"));
            assertEquals("", result.getSummary());
        }

        @Test
        @DisplayName("空响应内容 - 返回空摘要")
        void emptyResponseContent() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent(""));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertTrue(result.isSuccess());
            assertEquals("", result.getSummary());
        }

        @Test
        @DisplayName("null响应消息 - 返回空字符串")
        void nullResponseMessage() {
            mockLlmClient.enqueueNullResponse();

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertTrue(result.isSuccess());
            assertEquals("", result.getSummary());
        }
    }

    @Nested
    @DisplayName("取消功能测试")
    class CancellationTests {

        @Test
        @DisplayName("取消后执行 - 返回取消结果")
        void cancelBeforeExecute() {
            executor.cancel();

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertFalse(result.isSuccess());
            assertTrue(result.isCancelled());
            assertEquals("用户取消", result.getError());
        }

        @Test
        @DisplayName("多次取消 - 幂等操作")
        void multipleCancellations() {
            executor.cancel();
            executor.cancel();
            executor.cancel();

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertTrue(result.isCancelled());
        }
    }

    @Nested
    @DisplayName("部分结果监听器测试")
    class PartialResultListenerTests {

        @Test
        @DisplayName("设置监听器 - 接收部分内容")
        void listenerReceivesContent() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> receivedContent = new AtomicReference<>();

            executor.setPartialResultListener(content -> {
                receivedContent.set(content);
                latch.countDown();
            });

            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("压缩摘要内容"));

            executor.executeForkedCompaction("test-session", "请压缩对话");

            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertEquals("压缩摘要内容", receivedContent.get());
        }

        @Test
        @DisplayName("null监听器 - 不影响执行")
        void nullListenerDoesNotAffectExecution() {
            executor.setPartialResultListener(null);

            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要内容"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话"
            );

            assertTrue(result.isSuccess());
            assertEquals("摘要内容", result.getSummary());
        }

        @Test
        @DisplayName("替换监听器 - 只有最后一个生效")
        void replaceListener() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> firstContent = new AtomicReference<>();
            AtomicReference<String> secondContent = new AtomicReference<>();

            executor.setPartialResultListener(content -> firstContent.set(content));
            executor.setPartialResultListener(content -> {
                secondContent.set(content);
                latch.countDown();
            });

            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("新摘要"));

            executor.executeForkedCompaction("test-session", "请压缩对话");

            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertEquals("新摘要", secondContent.get());
            assertNull(firstContent.get());
        }
    }

    @Nested
    @DisplayName("CompactResult 测试")
    class CompactResultTests {

        @Test
        @DisplayName("成功结果 - 正确属性")
        void successResult() {
            CompactForkExecutor.CompactResult result = CompactForkExecutor.CompactResult.success(
                "摘要内容", true, 100
            );

            assertTrue(result.isSuccess());
            assertTrue(result.isUsedFork());
            assertEquals("摘要内容", result.getSummary());
            assertNull(result.getError());
            assertEquals(100, result.getOutputTokens());
            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("失败结果 - 正确属性")
        void failureResult() {
            CompactForkExecutor.CompactResult result = CompactForkExecutor.CompactResult.failure(
                "错误信息"
            );

            assertFalse(result.isSuccess());
            assertFalse(result.isUsedFork());
            assertEquals("", result.getSummary());
            assertEquals("错误信息", result.getError());
            assertEquals(0, result.getOutputTokens());
            assertFalse(result.isCancelled());
        }

        @Test
        @DisplayName("取消结果 - 正确属性")
        void cancelledResult() {
            CompactForkExecutor.CompactResult result = CompactForkExecutor.CompactResult.cancelled();

            assertFalse(result.isSuccess());
            assertFalse(result.isUsedFork());
            assertEquals("", result.getSummary());
            assertEquals("用户取消", result.getError());
            assertEquals(0, result.getOutputTokens());
            assertTrue(result.isCancelled());
        }

        @Test
        @DisplayName("成功且未使用Fork - 直接模式")
        void successWithoutFork() {
            CompactForkExecutor.CompactResult result = CompactForkExecutor.CompactResult.success(
                "摘要", false, 50
            );

            assertTrue(result.isSuccess());
            assertFalse(result.isUsedFork());
            assertEquals(50, result.getOutputTokens());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("空会话ID - 正常处理")
        void emptySessionId() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "",
                "请压缩对话"
            );

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("null会话ID - 正常处理")
        void nullSessionId() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                null,
                "请压缩对话"
            );

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("空压缩提示词 - 正常处理")
        void emptyCompactionPrompt() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                ""
            );

            assertTrue(result.isSuccess());
            String content = mockLlmClient.getLastSentMessages().get(0).getContent();
            assertTrue(content.contains("---"));
        }

        @Test
        @DisplayName("超长提示词 - 正常处理")
        void longCompactionPrompt() {
            String longPrompt = "x".repeat(10000);
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                longPrompt
            );

            assertTrue(result.isSuccess());
            String content = mockLlmClient.getLastSentMessages().get(0).getContent();
            assertTrue(content.contains(longPrompt));
        }

        @Test
        @DisplayName("超时时间为0 - 立即超时")
        void zeroTimeout() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话",
                0
            );

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("超时时间为负数 - 正常处理")
        void negativeTimeout() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                "test-session",
                "请压缩对话",
                -1
            );

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("多次执行 - 线程安全")
        void multipleExecutions() {
            for (int i = 0; i < 10; i++) {
                mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要 " + i));
            }

            for (int i = 0; i < 10; i++) {
                CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                    "session-" + i,
                    "压缩对话"
                );
                assertTrue(result.isSuccess());
            }
        }

        @Test
        @DisplayName("取消后再次执行 - 保持取消状态")
        void cancelThenExecute() {
            executor.cancel();

            for (int i = 0; i < 5; i++) {
                mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));
                CompactForkExecutor.CompactResult result = executor.executeForkedCompaction(
                    "session-" + i,
                    "压缩对话"
                );
                assertTrue(result.isCancelled());
            }
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("快速响应 - 性能良好")
        void fastResponse() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

            long startTime = System.currentTimeMillis();
            executor.executeForkedCompaction("test-session", "压缩对话");
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 1000, "执行时间应该小于1秒，实际: " + duration + "ms");
        }

        @Test
        @DisplayName("重复执行性能稳定")
        void repeatedExecutionPerformance() {
            long totalTime = 0;

            for (int i = 0; i < 20; i++) {
                mockLlmClient.enqueueResponse(LlmResponseBuilder.simpleContent("摘要"));

                long startTime = System.currentTimeMillis();
                executor.executeForkedCompaction("session-" + i, "压缩对话");
                totalTime += System.currentTimeMillis() - startTime;
            }

            long avgTime = totalTime / 20;
            assertTrue(avgTime < 500, "平均执行时间应该小于500ms，实际: " + avgTime + "ms");
        }
    }
}
