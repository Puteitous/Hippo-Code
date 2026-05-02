package com.example.agent.progress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SpinnerManagerTest {

    @Nested
    @DisplayName("ToolCallCard 状态测试")
    class ToolCallCardStateTests {

        @Test
        @DisplayName("新创建的卡片处于未运行状态")
        void testNewCardNotRunning() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);

            assertFalse(card.isRunning());
            assertFalse(card.isCompleted());
        }

        @Test
        @DisplayName("start() 后卡片处于运行状态")
        void testCardRunningAfterStart() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();

            assertTrue(card.isRunning());
            assertFalse(card.isCompleted());
        }

        @Test
        @DisplayName("completeSuccess() 后卡片处于完成状态")
        void testCardCompletedAfterSuccess() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.completeSuccess("test result");

            assertFalse(card.isRunning());
            assertTrue(card.isCompleted());
            assertEquals("✅", card.getCompletionMarker());
            assertEquals("成功", card.getCompletionStatus());
            assertEquals("test result", card.getCompletionDetail());
        }

        @Test
        @DisplayName("completeFailure() 后卡片处于完成状态")
        void testCardCompletedAfterFailure() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.completeFailure("error message");

            assertFalse(card.isRunning());
            assertTrue(card.isCompleted());
            assertEquals("❌", card.getCompletionMarker());
            assertEquals("失败", card.getCompletionStatus());
            assertEquals("error message", card.getCompletionDetail());
        }

        @Test
        @DisplayName("ask_user 工具不启动")
        void testAskUserToolDoesNotStart() {
            ToolCallCard card = new ToolCallCard("ask_user", "call-1", 0, 1, true);
            card.start();

            assertFalse(card.isRunning());
        }

        @Test
        @DisplayName("前台工具不注册到 SpinnerManager")
        void testForegroundToolDoesNotRegister() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, false);
            card.start();

            assertTrue(card.isRunning());
        }

        @Test
        @DisplayName("索引和总数正确存储")
        void testIndexAndTotalStored() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 2, 5, true);

            assertEquals(2, card.getIndex());
            assertEquals(5, card.getTotal());
            assertEquals("test_tool", card.getToolName());
        }

        @Test
        @DisplayName("运行时间计算正确")
        void testElapsedTimeCalculation() throws InterruptedException {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            Thread.sleep(100);

            String elapsed = card.getElapsedTime();

            assertNotNull(elapsed);
            assertTrue(elapsed.contains("ms") || elapsed.contains("s"));
        }

        @Test
        @DisplayName("updateStatus 更新当前状态")
        void testUpdateStatus() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.updateStatus("processing...");

            assertEquals("processing...", card.getCurrentStatus());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("快速完成不崩溃")
        void testRapidComplete() throws InterruptedException {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.completeSuccess("immediate");

            Thread.sleep(200);
        }

        @Test
        @DisplayName("重复完成不崩溃")
        void testDoubleComplete() throws InterruptedException {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.completeSuccess("first");
            card.completeSuccess("second");

            Thread.sleep(200);

            assertEquals("second", card.getCompletionDetail());
        }

        @Test
        @DisplayName("null 结果处理")
        void testNullResult() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.completeSuccess(null);

            assertTrue(card.isCompleted());
            assertEquals("", card.getCompletionDetail());
        }

        @Test
        @DisplayName("空字符串结果处理")
        void testEmptyResult() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            card.completeSuccess("");

            assertTrue(card.isCompleted());
            assertEquals("", card.getCompletionDetail());
        }

        @Test
        @DisplayName("工具名称为空不崩溃")
        void testEmptyToolName() {
            ToolCallCard card = new ToolCallCard("", "call-1", 0, 1, true);
            card.start();
            card.completeSuccess("result");

            assertTrue(card.isCompleted());
        }

        @Test
        @DisplayName("长结果被截断")
        void testLongResultTruncated() {
            ToolCallCard card = new ToolCallCard("test_tool", "call-1", 0, 1, true);
            card.start();
            String longResult = "a".repeat(100);
            card.completeSuccess(longResult);

            String detail = card.getCompletionDetail();

            assertTrue(detail.length() <= 63);
            assertTrue(detail.endsWith("..."));
        }
    }

    @Nested
    @DisplayName("工具回调接口测试")
    class ToolExecutionCallbackTests {

        @Test
        @DisplayName("onToolStart 接收 runInBackground 参数为 true")
        void testOnToolStartReceivesRunInBackgroundTrue() {
            AtomicBoolean capturedRunInBackground = new AtomicBoolean(false);

            ToolExecutionCallback callback = new ToolExecutionCallback() {
                @Override
                public void onToolStart(com.example.agent.llm.model.ToolCall toolCall, int index, int total, boolean runInBackground) {
                    capturedRunInBackground.set(runInBackground);
                }

                @Override
                public void onToolComplete(com.example.agent.llm.model.ToolCall toolCall, com.example.agent.tools.concurrent.ToolExecutionResult result, int index, int total) {
                }
            };

            com.example.agent.llm.model.ToolCall toolCall = new com.example.agent.llm.model.ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new com.example.agent.llm.model.FunctionCall("test_tool", "{}"));

            callback.onToolStart(toolCall, 0, 1, true);

            assertTrue(capturedRunInBackground.get());
        }

        @Test
        @DisplayName("前台工具 runInBackground 为 false")
        void testForegroundToolRunInBackgroundFalse() {
            AtomicBoolean capturedRunInBackground = new AtomicBoolean(true);

            ToolExecutionCallback callback = new ToolExecutionCallback() {
                @Override
                public void onToolStart(com.example.agent.llm.model.ToolCall toolCall, int index, int total, boolean runInBackground) {
                    capturedRunInBackground.set(runInBackground);
                }

                @Override
                public void onToolComplete(com.example.agent.llm.model.ToolCall toolCall, com.example.agent.tools.concurrent.ToolExecutionResult result, int index, int total) {
                }
            };

            com.example.agent.llm.model.ToolCall toolCall = new com.example.agent.llm.model.ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new com.example.agent.llm.model.FunctionCall("test_tool", "{}"));

            callback.onToolStart(toolCall, 0, 1, false);

            assertFalse(capturedRunInBackground.get());
        }

        @Test
        @DisplayName("onToolComplete 接收成功结果")
        void testOnToolCompleteSuccess() {
            AtomicBoolean receivedSuccess = new AtomicBoolean(false);
            AtomicReference<String> receivedToolName = new AtomicReference<>();

            ToolExecutionCallback callback = new ToolExecutionCallback() {
                @Override
                public void onToolStart(com.example.agent.llm.model.ToolCall toolCall, int index, int total, boolean runInBackground) {
                }

                @Override
                public void onToolComplete(com.example.agent.llm.model.ToolCall toolCall, com.example.agent.tools.concurrent.ToolExecutionResult result, int index, int total) {
                    receivedSuccess.set(result.isSuccess());
                    receivedToolName.set(result.getToolName());
                }
            };

            com.example.agent.llm.model.ToolCall toolCall = new com.example.agent.llm.model.ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new com.example.agent.llm.model.FunctionCall("test_tool", "{}"));

            com.example.agent.tools.concurrent.ToolExecutionResult result = com.example.agent.tools.concurrent.ToolExecutionResult.builder()
                    .index(0)
                    .toolCallId("call-1")
                    .toolName("test_tool")
                    .result("success")
                    .success(true)
                    .executionTimeMs(100)
                    .build();

            callback.onToolComplete(toolCall, result, 0, 1);

            assertTrue(receivedSuccess.get());
            assertEquals("test_tool", receivedToolName.get());
        }

        @Test
        @DisplayName("onToolComplete 接收失败结果")
        void testOnToolCompleteFailure() {
            AtomicBoolean receivedSuccess = new AtomicBoolean(true);
            AtomicReference<String> receivedError = new AtomicReference<>();

            ToolExecutionCallback callback = new ToolExecutionCallback() {
                @Override
                public void onToolStart(com.example.agent.llm.model.ToolCall toolCall, int index, int total, boolean runInBackground) {
                }

                @Override
                public void onToolComplete(com.example.agent.llm.model.ToolCall toolCall, com.example.agent.tools.concurrent.ToolExecutionResult result, int index, int total) {
                    receivedSuccess.set(result.isSuccess());
                    receivedError.set(result.getErrorMessage());
                }
            };

            com.example.agent.llm.model.ToolCall toolCall = new com.example.agent.llm.model.ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new com.example.agent.llm.model.FunctionCall("test_tool", "{}"));

            com.example.agent.tools.concurrent.ToolExecutionResult result = com.example.agent.tools.concurrent.ToolExecutionResult.builder()
                    .index(0)
                    .toolCallId("call-1")
                    .toolName("test_tool")
                    .success(false)
                    .errorMessage("Tool failed")
                    .executionTimeMs(50)
                    .build();

            callback.onToolComplete(toolCall, result, 0, 1);

            assertFalse(receivedSuccess.get());
            assertEquals("Tool failed", receivedError.get());
        }
    }

    @Nested
    @DisplayName("SpinnerManager 单例测试")
    class SpinnerManagerSingletonTests {

        @Test
        @DisplayName("getInstance 返回相同实例")
        void testGetInstanceReturnsSameInstance() {
            SpinnerManager instance1 = SpinnerManager.getInstance();
            SpinnerManager instance2 = SpinnerManager.getInstance();

            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("clear() 不抛出异常")
        void testClearDoesNotThrow() {
            assertDoesNotThrow(() -> {
                SpinnerManager.getInstance().clear();
            });
        }
    }

    static class AtomicReference<T> {
        private volatile T value;

        AtomicReference() {
            this.value = null;
        }

        AtomicReference(T initialValue) {
            this.value = initialValue;
        }

        T get() {
            return value;
        }

        void set(T value) {
            this.value = value;
        }
    }
}
