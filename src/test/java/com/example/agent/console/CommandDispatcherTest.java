package com.example.agent.console;

import com.example.agent.config.Config;
import com.example.agent.core.AgentContext;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.service.ConversationManager;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandDispatcherTest {

    private AgentContext context;
    private AgentUi ui;
    private InputHandler inputHandler;
    private Config config;
    private ConversationManager conversationManager;
    private TokenMetricsCollector tokenMetricsCollector;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        context = mock(AgentContext.class);
        ui = mock(AgentUi.class);
        inputHandler = mock(InputHandler.class);
        config = mock(Config.class);
        conversationManager = mock(ConversationManager.class);
        tokenMetricsCollector = mock(TokenMetricsCollector.class);

        when(context.getConfig()).thenReturn(config);
        when(context.getConversationManager()).thenReturn(conversationManager);
        when(context.getTokenMetricsCollector()).thenReturn(tokenMetricsCollector);

        dispatcher = new CommandDispatcher(context, ui, inputHandler);
    }

    @Nested
    @DisplayName("dispatch方法测试")
    class DispatchTests {

        @Test
        @DisplayName("null输入返回CONTINUE")
        void testNullInput() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch(null);
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
        }

        @Test
        @DisplayName("空输入返回CONTINUE")
        void testEmptyInput() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
        }

        @Test
        @DisplayName("空白输入返回CONTINUE")
        void testWhitespaceInput() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("   ");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
        }

        @Test
        @DisplayName("exit命令返回EXIT")
        void testExitCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("exit");
            
            assertEquals(CommandDispatcher.CommandResult.EXIT, result);
            verify(ui).printGoodbye();
        }

        @Test
        @DisplayName("quit命令返回EXIT")
        void testQuitCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("quit");
            
            assertEquals(CommandDispatcher.CommandResult.EXIT, result);
            verify(ui).printGoodbye();
        }

        @Test
        @DisplayName("EXIT大写返回EXIT")
        void testExitCommandUppercase() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("EXIT");
            
            assertEquals(CommandDispatcher.CommandResult.EXIT, result);
        }

        @Test
        @DisplayName("help命令返回CONTINUE")
        void testHelpCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("help");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
            verify(ui).printHelp();
        }

        @Test
        @DisplayName("clear命令返回CONTINUE")
        void testClearCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("clear");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
            verify(ui).clearScreen();
        }

        @Test
        @DisplayName("reset命令返回CONTINUE")
        void testResetCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("reset");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
            verify(conversationManager).reset();
        }

        @Test
        @DisplayName("config命令返回CONTINUE")
        void testConfigCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("config");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
            verify(ui).printConfig();
        }

        @Test
        @DisplayName("tokens命令返回CONTINUE")
        void testTokensCommand() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("tokens");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
            verify(tokenMetricsCollector).printDailySummary();
        }

        @Test
        @DisplayName("showlog命令返回CONTINUE")
        void testShowlogCommand() throws UserInterruptException, EndOfFileException {
            dispatcher.setCurrentConversationId("test-conversation-id");
            
            CommandDispatcher.CommandResult result = dispatcher.dispatch("showlog");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
            verify(ui).showLastConversationLog("test-conversation-id");
        }

        @Test
        @DisplayName("普通输入返回PROCESS_INPUT")
        void testNormalInput() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("hello world");
            
            assertEquals(CommandDispatcher.CommandResult.PROCESS_INPUT, result);
        }

        @Test
        @DisplayName("multi命令处理多行输入")
        void testMultiCommand() throws UserInterruptException, EndOfFileException {
            when(inputHandler.readMultilineInput()).thenReturn("test input");
            
            CommandDispatcher.CommandResult result = dispatcher.dispatch("multi");
            
            verify(inputHandler).readMultilineInput();
        }

        @Test
        @DisplayName("三引号命令处理多行输入")
        void testTripleQuoteCommand() throws UserInterruptException, EndOfFileException {
            when(inputHandler.readMultilineInput()).thenReturn(null);
            
            CommandDispatcher.CommandResult result = dispatcher.dispatch("\"\"\"");
            
            verify(inputHandler).readMultilineInput();
        }
    }

    @Nested
    @DisplayName("validateConfig方法测试")
    class ValidateConfigTests {

        @Test
        @DisplayName("有效API Key返回true")
        void testValidApiKey() {
            when(config.getApiKey()).thenReturn("valid-api-key");
            
            boolean result = dispatcher.validateConfig();
            
            assertTrue(result);
        }

        @Test
        @DisplayName("null API Key返回false")
        void testNullApiKey() {
            when(config.getApiKey()).thenReturn(null);
            
            boolean result = dispatcher.validateConfig();
            
            assertFalse(result);
            verify(ui).printConfigValidationError();
        }

        @Test
        @DisplayName("空API Key返回false")
        void testEmptyApiKey() {
            when(config.getApiKey()).thenReturn("");
            
            boolean result = dispatcher.validateConfig();
            
            assertFalse(result);
            verify(ui).printConfigValidationError();
        }
    }

    @Nested
    @DisplayName("会话ID管理测试")
    class ConversationIdTests {

        @Test
        @DisplayName("设置和获取会话ID")
        void testSetAndGetConversationId() {
            dispatcher.setCurrentConversationId("conv-123");
            
            assertEquals("conv-123", dispatcher.getCurrentConversationId());
        }

        @Test
        @DisplayName("null会话ID")
        void testNullConversationId() {
            dispatcher.setCurrentConversationId(null);
            
            assertNull(dispatcher.getCurrentConversationId());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("命令前后有空格")
        void testCommandWithSpaces() throws UserInterruptException, EndOfFileException {
            CommandDispatcher.CommandResult result = dispatcher.dispatch("  exit  ");
            
            assertEquals(CommandDispatcher.CommandResult.EXIT, result);
        }

        @Test
        @DisplayName("多行输入返回空")
        void testMultilineInputReturnsEmpty() throws UserInterruptException, EndOfFileException {
            when(inputHandler.readMultilineInput()).thenReturn("");
            
            CommandDispatcher.CommandResult result = dispatcher.dispatch("multi");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
        }

        @Test
        @DisplayName("多行输入返回空白")
        void testMultilineInputReturnsWhitespace() throws UserInterruptException, EndOfFileException {
            when(inputHandler.readMultilineInput()).thenReturn("   ");
            
            CommandDispatcher.CommandResult result = dispatcher.dispatch("multi");
            
            assertEquals(CommandDispatcher.CommandResult.CONTINUE, result);
        }
    }
}
