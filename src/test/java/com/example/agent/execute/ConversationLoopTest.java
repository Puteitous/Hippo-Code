package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.console.InputHandler;
import com.example.agent.core.AgentContext;
import com.example.agent.intent.IntentRecognizer;
import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.plan.*;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import com.example.agent.logging.LogDirectoryManager;
import org.jline.reader.UserInterruptException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationLoopTest {

    @TempDir
    Path tempDir;

    private AgentContext context;
    private AgentTurnExecutor turnExecutor;
    private ConversationManager conversationManager;
    private TokenEstimator tokenEstimator;
    private InputHandler inputHandler;
    private AgentUi ui;
    private IntentRecognizer intentRecognizer;
    private TaskPlanner taskPlanner;
    private PlanExecutor planExecutor;
    private ConversationLoop conversationLoop;
    private MockedStatic<LogDirectoryManager> logDirMock;

    @BeforeEach
    void setUp() {
        context = mock(AgentContext.class);
        turnExecutor = mock(AgentTurnExecutor.class);
        conversationManager = mock(ConversationManager.class);
        tokenEstimator = mock(TokenEstimator.class);
        inputHandler = mock(InputHandler.class);
        ui = mock(AgentUi.class);
        intentRecognizer = mock(IntentRecognizer.class);
        taskPlanner = mock(TaskPlanner.class);
        planExecutor = mock(PlanExecutor.class);

        when(context.getConversationManager()).thenReturn(conversationManager);
        when(context.getTokenEstimator()).thenReturn(tokenEstimator);
        when(tokenEstimator.estimateTextTokens(anyString())).thenReturn(100);
        when(inputHandler.getMaxInputTokens()).thenReturn(10000);
        when(conversationManager.getHistory()).thenReturn(new ArrayList<>());
        when(intentRecognizer.isEnabled()).thenReturn(true);
        when(taskPlanner.isEnabled()).thenReturn(true);

        logDirMock = mockStatic(LogDirectoryManager.class);
        logDirMock.when(() -> LogDirectoryManager.getConversationLogFile(anyString(), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    String convId = invocation.getArgument(0);
                    LocalDate date = invocation.getArgument(1);
                    return tempDir.resolve("conversations")
                            .resolve(date.toString())
                            .resolve("conv_" + convId + ".log");
                });

        conversationLoop = new ConversationLoop(
                context, turnExecutor, inputHandler, ui,
                intentRecognizer, taskPlanner, planExecutor
        );
    }

    @AfterEach
    void tearDown() {
        if (logDirMock != null) {
            logDirMock.close();
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("空输入和空白输入应正常处理")
        void testEmptyInput(String input) {
            when(tokenEstimator.estimateTextTokens(anyString())).thenReturn(0);

            assertDoesNotThrow(() -> conversationLoop.processUserInput(input));
        }

        @Test
        @DisplayName("超长输入应被处理")
        void testLongInput() {
            String longInput = "a".repeat(100000);
            when(tokenEstimator.estimateTextTokens(longInput)).thenReturn(50000);
            when(inputHandler.handleLongInput(longInput, 50000)).thenReturn("truncated");

            conversationLoop.processUserInput(longInput);

            verify(inputHandler).handleLongInput(longInput, 50000);
        }

        @Test
        @DisplayName("超长输入返回null时停止处理")
        void testLongInputReturnsNull() throws Exception {
            String longInput = "a".repeat(100000);
            when(tokenEstimator.estimateTextTokens(longInput)).thenReturn(50000);
            when(inputHandler.handleLongInput(longInput, 50000)).thenReturn(null);

            conversationLoop.processUserInput(longInput);

            verify(turnExecutor, never()).execute(any(), any());
        }
    }

    @Nested
    @DisplayName("意图识别测试")
    class IntentRecognitionTests {

        @Test
        @DisplayName("意图识别成功")
        void testSuccessfulIntentRecognition() {
            IntentResult intent = IntentResult.of(IntentType.CODE_GENERATION, 0.9);
            when(intentRecognizer.recognize(anyString(), anyList())).thenReturn(intent);

            conversationLoop.processUserInput("写一个函数");

            verify(intentRecognizer).recognize(anyString(), anyList());
            assertEquals(intent, conversationLoop.getLastIntentResult());
        }

        @Test
        @DisplayName("意图识别异常时返回unknown")
        void testIntentRecognitionException() {
            when(intentRecognizer.recognize(anyString(), anyList()))
                    .thenThrow(new RuntimeException("Intent recognition failed"));

            conversationLoop.processUserInput("测试输入");

            assertEquals(IntentType.UNKNOWN, conversationLoop.getLastIntentResult().getType());
        }

        @Test
        @DisplayName("意图识别器禁用时不进行识别")
        void testIntentRecognizerDisabled() {
            when(intentRecognizer.isEnabled()).thenReturn(false);

            conversationLoop.processUserInput("测试输入");

            verify(intentRecognizer, never()).recognize(anyString(), anyList());
        }

        @Test
        @DisplayName("意图识别器为null时不进行识别")
        void testIntentRecognizerNull() {
            ConversationLoop loopWithoutIntent = new ConversationLoop(
                    context, turnExecutor, inputHandler, ui
            );

            loopWithoutIntent.processUserInput("测试输入");

            assertNull(loopWithoutIntent.getLastIntentResult());
        }
    }

    @Nested
    @DisplayName("执行计划测试")
    class ExecutionPlanTests {

        @Test
        @DisplayName("创建执行计划成功")
        void testCreateExecutionPlan() {
            IntentResult intent = IntentResult.of(IntentType.CODE_GENERATION, 0.9);
            ExecutionPlan plan = ExecutionPlan.empty(intent);
            when(intentRecognizer.recognize(anyString(), anyList())).thenReturn(intent);
            when(taskPlanner.plan(any(), any())).thenReturn(plan);

            conversationLoop.processUserInput("写一个函数");

            verify(taskPlanner).plan(any(), any());
            assertEquals(plan, conversationLoop.getLastExecutionPlan());
        }

        @Test
        @DisplayName("计划器异常时返回空计划")
        void testPlannerException() {
            IntentResult intent = IntentResult.of(IntentType.CODE_GENERATION, 0.9);
            when(intentRecognizer.recognize(anyString(), anyList())).thenReturn(intent);
            when(taskPlanner.plan(any(), any())).thenThrow(new RuntimeException("Planning failed"));

            conversationLoop.processUserInput("写一个函数");

            assertNotNull(conversationLoop.getLastExecutionPlan());
            assertTrue(conversationLoop.getLastExecutionPlan().isEmpty());
        }

        @Test
        @DisplayName("计划器禁用时不创建计划")
        void testPlannerDisabled() {
            when(taskPlanner.isEnabled()).thenReturn(false);

            conversationLoop.processUserInput("测试输入");

            verify(taskPlanner, never()).plan(any(), any());
        }

        @Test
        @DisplayName("空步骤列表返回空结果")
        void testEmptyStepsList() {
            IntentResult intent = IntentResult.of(IntentType.QUESTION, 0.8);
            ExecutionPlan emptyPlan = ExecutionPlan.empty(intent);
            when(intentRecognizer.recognize(anyString(), anyList())).thenReturn(intent);
            when(taskPlanner.plan(any(), any())).thenReturn(emptyPlan);

            conversationLoop.processUserInput("什么是Java");

            assertTrue(conversationLoop.getLastExecutionPlan().isEmpty());
            assertEquals(0, conversationLoop.getLastExecutionPlan().getStepCount());
        }
    }

    @Nested
    @DisplayName("中断测试")
    class InterruptTests {

        @Test
        @DisplayName("中断执行")
        void testInterrupt() {
            conversationLoop.interrupt();

            verify(turnExecutor).setInterrupted(true);
        }

        @Test
        @DisplayName("中断后停止处理")
        void testInterruptStopsProcessing() throws Exception {
            when(turnExecutor.isInterrupted()).thenReturn(true);

            conversationLoop.processUserInput("测试");

            verify(turnExecutor, never()).execute(any(), any());
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("LlmApiException处理")
        void testLlmApiException() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new LlmApiException("API Error", 500, null));

            assertDoesNotThrow(() -> conversationLoop.processUserInput("测试"));
        }

        @Test
        @DisplayName("LlmTimeoutException处理")
        void testLlmTimeoutException() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new LlmTimeoutException("Timeout", 30, null));

            assertDoesNotThrow(() -> conversationLoop.processUserInput("测试"));
        }

        @Test
        @DisplayName("LlmConnectionException处理")
        void testLlmConnectionException() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new LlmConnectionException("Connection failed", "http://test.com", null));

            assertDoesNotThrow(() -> conversationLoop.processUserInput("测试"));
        }

        @Test
        @DisplayName("RuntimeException with 'Interrupted' message")
        void testInterruptedRuntimeException() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new RuntimeException("Interrupted"));

            assertThrows(UserInterruptException.class, () -> conversationLoop.processUserInput("测试"));
        }

        @Test
        @DisplayName("其他RuntimeException处理")
        void testOtherRuntimeException() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new RuntimeException("Unexpected error"));

            assertDoesNotThrow(() -> conversationLoop.processUserInput("测试"));
        }
    }

    @Nested
    @DisplayName("空响应重试测试")
    class EmptyResponseRetryTests {

        @Test
        @DisplayName("空响应重试后成功")
        void testEmptyResponseRetrySuccess() throws Exception {
            when(turnExecutor.execute(any(), any()))
                    .thenReturn(AgentTurnResult.EMPTY_RESPONSE)
                    .thenReturn(AgentTurnResult.DONE);

            conversationLoop.processUserInput("测试");

            verify(turnExecutor, times(2)).execute(any(), any());
        }

        @Test
        @DisplayName("空响应超过最大重试次数")
        void testEmptyResponseMaxRetries() throws Exception {
            when(turnExecutor.execute(any(), any())).thenReturn(AgentTurnResult.EMPTY_RESPONSE);

            conversationLoop.processUserInput("测试");

            verify(turnExecutor, times(3)).execute(any(), any());
        }
    }

    @Nested
    @DisplayName("历史记录精简测试")
    class HistoryTrimTests {

        @Test
        @DisplayName("调用历史记录精简")
        void testHistoryTrim() throws Exception {
            when(turnExecutor.execute(any(), any())).thenReturn(AgentTurnResult.DONE);
            conversationLoop.processUserInput("测试");

            verify(conversationManager).trimHistory(any());
        }
    }

    @Nested
    @DisplayName("会话ID测试")
    class ConversationIdTests {

        @Test
        @DisplayName("获取当前会话ID")
        void testGetCurrentConversationId() throws Exception {
            assertNull(conversationLoop.getCurrentConversationId());

            when(turnExecutor.execute(any(), any())).thenReturn(AgentTurnResult.DONE);
            conversationLoop.processUserInput("测试");

            assertNotNull(conversationLoop.getCurrentConversationId());
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("基本构造函数")
        void testBasicConstructor() {
            ConversationLoop basicLoop = new ConversationLoop(
                    context, turnExecutor, inputHandler, ui
            );

            assertNotNull(basicLoop);
        }

        @Test
        @DisplayName("带意图识别器的构造函数")
        void testConstructorWithIntentRecognizer() {
            ConversationLoop loopWithIntent = new ConversationLoop(
                    context, turnExecutor, inputHandler, ui, intentRecognizer
            );

            assertNotNull(loopWithIntent);
        }

        @Test
        @DisplayName("带计划器的构造函数")
        void testConstructorWithPlanner() {
            ConversationLoop loopWithPlanner = new ConversationLoop(
                    context, turnExecutor, inputHandler, ui, intentRecognizer, taskPlanner
            );

            assertNotNull(loopWithPlanner);
        }

        @Test
        @DisplayName("完整构造函数")
        void testFullConstructor() {
            ConversationLoop fullLoop = new ConversationLoop(
                    context, turnExecutor, inputHandler, ui,
                    intentRecognizer, taskPlanner, planExecutor
            );

            assertNotNull(fullLoop);
        }
    }

    @Nested
    @DisplayName("可重试错误判断测试")
    class RetryableErrorTests {

        @Test
        @DisplayName("超时错误可重试")
        void testTimeoutRetryable() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new LlmTimeoutException("Timeout", 30, null));

            conversationLoop.processUserInput("测试");

            verify(ui).println(contains("retry"));
        }

        @Test
        @DisplayName("连接错误可重试")
        void testConnectionRetryable() throws Exception {
            when(turnExecutor.execute(any(), any())).thenThrow(new LlmConnectionException("Connection failed", "http://test.com", null));

            conversationLoop.processUserInput("测试");

            verify(ui).println(contains("retry"));
        }

        @Test
        @DisplayName("服务器错误可重试")
        void testServerErrorRetryable() throws Exception {
            LlmApiException serverError = mock(LlmApiException.class);
            when(serverError.isServerError()).thenReturn(true);
            when(turnExecutor.execute(any(), any())).thenThrow(serverError);

            conversationLoop.processUserInput("测试");

            verify(ui).println(contains("retry"));
        }

        @Test
        @DisplayName("限流错误可重试")
        void testRateLimitedRetryable() throws Exception {
            LlmApiException rateLimited = mock(LlmApiException.class);
            when(rateLimited.isRateLimited()).thenReturn(true);
            when(turnExecutor.execute(any(), any())).thenThrow(rateLimited);

            conversationLoop.processUserInput("测试");

            verify(ui).println(contains("retry"));
        }
    }
}
