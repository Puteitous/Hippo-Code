package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.service.ConversationManager;
import com.example.agent.tools.ToolExecutionException;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallProcessorTest {

    private ToolRegistry toolRegistry;
    private ConcurrentToolExecutor executor;
    private ConversationManager conversationManager;
    private AgentUi ui;
    private ToolCallProcessor processor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        executor = new ConcurrentToolExecutor(toolRegistry);
        conversationManager = mock(ConversationManager.class);
        ui = mock(AgentUi.class);
        processor = new ToolCallProcessor(executor, conversationManager, ui);
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
        @DisplayName("空工具调用列表应正常处理")
        void testEmptyToolCallsList() {
            List<ToolCall> toolCalls = new ArrayList<>();
            
            assertDoesNotThrow(() -> processor.processToolCallsConcurrently(toolCalls, null));
            
            verify(conversationManager, never()).addToolResult(any(), any(), any());
        }

        @Test
        @DisplayName("null工具调用列表应正常处理")
        void testNullToolCallsList() {
            assertDoesNotThrow(() -> processor.processToolCallsConcurrently(null, null));
        }

        @Test
        @DisplayName("工具名称为null应跳过")
        void testNullToolName() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", null, "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager, never()).addToolResult(any(), any(), any());
        }

        @Test
        @DisplayName("工具名称为空字符串应跳过")
        void testEmptyToolName() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager, never()).addToolResult(any(), any(), any());
        }

        @Test
        @DisplayName("Function为null应跳过")
        void testNullFunction() {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(null);
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(toolCall);
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager, never()).addToolResult(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("工具不存在测试")
    class ToolNotFoundTests {

        @Test
        @DisplayName("工具不存在应返回失败结果，不崩溃")
        void testToolNotExist() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "nonexistent_tool", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager).addToolResult(
                eq("call-1"),
                eq("nonexistent_tool"),
                contains("Error:")
            );
        }

        @Test
        @DisplayName("多个工具调用中部分不存在应继续执行其他")
        void testPartialToolNotExist() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("existing_tool", "success result"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "existing_tool", "{}"));
            toolCalls.add(createToolCall("call-2", "nonexistent_tool", "{}"));
            toolCalls.add(createToolCall("call-3", "existing_tool", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager, times(3)).addToolResult(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("成功执行测试")
    class SuccessfulExecutionTests {

        @Test
        @DisplayName("单个工具调用成功执行")
        void testSingleToolCallSuccess() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("test_tool", "test result"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", "{\"arg\": \"value\"}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager).addToolResult("call-1", "test_tool", "test result");
        }

        @Test
        @DisplayName("多个工具调用并发执行")
        void testMultipleToolCallsConcurrent() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("tool_a", "result_a"));
            toolRegistry.register(new MockToolExecutor("tool_b", "result_b"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "tool_a", "{}"));
            toolCalls.add(createToolCall("call-2", "tool_b", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager).addToolResult("call-1", "tool_a", "result_a");
            verify(conversationManager).addToolResult("call-2", "tool_b", "result_b");
        }
    }

    @Nested
    @DisplayName("参数解析测试")
    class ArgumentParsingTests {

        @Test
        @DisplayName("无效JSON参数应返回错误")
        void testInvalidJsonArguments() {
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", "not valid json"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager).addToolResult(
                eq("call-1"),
                eq("test_tool"),
                contains("Error:")
            );
        }

        @Test
        @DisplayName("null参数应正常处理")
        void testNullArguments() {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new FunctionCall("test_tool", null));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(toolCall);
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager).addToolResult(
                eq("call-1"),
                eq("test_tool"),
                any()
            );
        }

        @Test
        @DisplayName("空JSON对象参数应正常处理")
        void testEmptyJsonArguments() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("test_tool", "success"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager).addToolResult("call-1", "test_tool", "success");
        }
    }

    @Nested
    @DisplayName("日志记录测试")
    class LoggingTests {

        @Test
        @DisplayName("成功执行应记录日志")
        void testLogSuccessfulExecution() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("test_tool", "result"));
            
            ConversationLogger logger = mock(ConversationLogger.class);
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", "{\"key\": \"value\"}"));
            
            processor.processToolCallsConcurrently(toolCalls, logger);
            
            verify(logger).logToolCall(
                eq("test_tool"),
                eq("{\"key\": \"value\"}"),
                eq("result"),
                anyLong(),
                eq(true)
            );
        }

        @Test
        @DisplayName("失败执行应记录日志")
        void testLogFailedExecution() {
            ConversationLogger logger = mock(ConversationLogger.class);
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "nonexistent", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, logger);
            
            verify(logger).logToolCall(
                eq("nonexistent"),
                eq("{}"),
                contains("Unknown tool"),
                anyLong(),
                eq(false)
            );
        }

        @Test
        @DisplayName("null日志应不崩溃")
        void testNullLogger() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("test_tool", "result"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "test_tool", "{}"));
            
            assertDoesNotThrow(() -> processor.processToolCallsConcurrently(toolCalls, null));
        }
    }

    @Nested
    @DisplayName("混合场景测试")
    class MixedScenarioTests {

        @Test
        @DisplayName("有效和无效工具调用混合")
        void testMixedValidInvalidToolCalls() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("valid_tool", "success"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(createToolCall("call-1", "valid_tool", "{}"));
            toolCalls.add(createToolCall("call-2", null, "{}"));
            toolCalls.add(createToolCall("call-3", "", "{}"));
            toolCalls.add(createToolCall("call-4", "nonexistent_tool", "{}"));
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager, times(2)).addToolResult(any(), any(), any());
        }

        @Test
        @DisplayName("大量工具调用并发执行")
        void testLargeNumberOfToolCalls() throws ToolExecutionException {
            toolRegistry.register(new MockToolExecutor("test_tool", "result"));
            
            List<ToolCall> toolCalls = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                toolCalls.add(createToolCall("call-" + i, "test_tool", "{}"));
            }
            
            processor.processToolCallsConcurrently(toolCalls, null);
            
            verify(conversationManager, times(100)).addToolResult(any(), any(), any());
        }
    }

    private static class MockToolExecutor implements com.example.agent.tools.ToolExecutor {
        private final String name;
        private final String result;

        MockToolExecutor(String name, String result) {
            this.name = name;
            this.result = result;
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
            return result;
        }
    }
}
