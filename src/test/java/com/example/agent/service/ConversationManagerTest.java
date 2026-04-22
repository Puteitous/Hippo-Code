package com.example.agent.service;

import com.example.agent.context.Compressor;
import com.example.agent.context.ContextManager;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationManagerTest {

    private TokenEstimator tokenEstimator;
    private LlmClient llmClient;
    private Compressor compressor;
    private ContextConfig config;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        llmClient = mock(LlmClient.class);
        compressor = mock(Compressor.class);
        config = new ContextConfig();
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("基本构造函数")
        void testBasicConstructor() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator, llmClient);
            
            assertNotNull(manager);
            assertEquals(1, manager.getMessageCount());
            assertNotNull(manager.getContextManager());
        }

        @Test
        @DisplayName("带Config构造")
        void testConstructorWithConfig() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator, llmClient, config);
            
            assertNotNull(manager);
            assertEquals(config, manager.getConfig());
            assertNotNull(manager.getContextManager());
        }

        @Test
        @DisplayName("null TokenEstimator抛出异常")
        void testNullTokenEstimator() {
            assertThrows(IllegalArgumentException.class, () -> {
                new ConversationManager("System prompt", null, llmClient);
            });
        }

        @Test
        @DisplayName("null LlmClient抛出异常")
        void testNullLlmClient() {
            assertThrows(IllegalArgumentException.class, () -> {
                new ConversationManager("System prompt", tokenEstimator, null);
            });
        }

        @Test
        @DisplayName("null systemPrompt使用空字符串")
        void testNullSystemPrompt() {
            ConversationManager manager = new ConversationManager(null, tokenEstimator, llmClient);
            
            assertEquals(1, manager.getMessageCount());
            assertEquals("", manager.getHistory().get(0).getContent());
        }

        @Test
        @DisplayName("null Config使用默认配置")
        void testNullConfig() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient, null);
            
            assertNotNull(manager.getConfig());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("空输入添加用户消息")
        void testAddEmptyUserMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            manager.addUserMessage("");
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("null输入添加用户消息")
        void testAddNullUserMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            manager.addUserMessage(null);
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("null助手消息不添加")
        void testAddNullAssistantMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            manager.addAssistantMessage(null);
            
            assertEquals(1, manager.getMessageCount());
        }

        @Test
        @DisplayName("null工具结果正常处理")
        void testAddNullToolResult() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            assertDoesNotThrow(() -> manager.addToolResult(null, "tool", "result"));
            assertDoesNotThrow(() -> manager.addToolResult("id", null, "result"));
            assertDoesNotThrow(() -> manager.addToolResult("id", "tool", null));
        }
    }

    @Nested
    @DisplayName("消息管理测试")
    class MessageManagementTests {

        @Test
        @DisplayName("添加用户消息")
        void testAddUserMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            manager.addUserMessage("Hello");
            
            assertEquals(2, manager.getMessageCount());
            assertEquals("Hello", manager.getHistory().get(1).getContent());
        }

        @Test
        @DisplayName("添加助手消息")
        void testAddAssistantMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            Message assistantMsg = Message.assistant("Hi there!");
            
            manager.addAssistantMessage(assistantMsg);
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("添加工具结果")
        void testAddToolResult() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            manager.addToolResult("call-1", "bash", "file.txt");
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("重置会话")
        void testReset() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            manager.addAssistantMessage(Message.assistant("Hi"));
            
            manager.reset();
            
            assertEquals(1, manager.getMessageCount());
            assertEquals("System", manager.getHistory().get(0).getContent());
        }

        @Test
        @DisplayName("获取历史记录")
        void testGetHistory() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            
            List<Message> history = manager.getHistory();
            
            assertEquals(2, history.size());
        }

        @Test
        @DisplayName("获取推理上下文（带自动压缩）")
        void testGetContextForInference() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            
            List<Message> context = manager.getContextForInference();
            
            assertNotNull(context);
            assertFalse(context.isEmpty());
        }
    }

    @Nested
    @DisplayName("Token计数测试")
    class TokenCountTests {

        @Test
        @DisplayName("获取Token计数")
        void testGetTokenCount() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator, llmClient);
            
            int tokens = manager.getTokenCount();
            
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("获取Token使用率")
        void testGetTokenUsageRatio() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator, llmClient);
            
            double ratio = manager.getTokenUsageRatio();
            
            assertTrue(ratio >= 0 && ratio <= 1.0);
        }

        @Test
        @DisplayName("添加消息后Token增加")
        void testTokenCountIncreases() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            int initialTokens = manager.getTokenCount();
            
            manager.addUserMessage("Hello World");
            
            assertTrue(manager.getTokenCount() > initialTokens);
        }

        @Test
        @DisplayName("获取消息计数")
        void testGetMessageCount() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            assertEquals(1, manager.getMessageCount());
            
            manager.addUserMessage("Hello");
            
            assertEquals(2, manager.getMessageCount());
        }
    }

    @Nested
    @DisplayName("工具结果压缩测试")
    class ToolResultCompressionTests {

        @Test
        @DisplayName("获取Compressor")
        void testGetCompressor() {
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, llmClient, config
            );
            
            assertNotNull(manager.getToolResultCompressor());
        }

        @Test
        @DisplayName("ContextManager正确初始化")
        void testContextManagerInitialized() {
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, llmClient, config
            );
            
            ContextManager contextManager = manager.getContextManager();
            
            assertNotNull(contextManager);
            assertNotNull(contextManager.getBudget());
            assertNotNull(contextManager.getContextWindow());
        }
    }

    @Nested
    @DisplayName("配置访问测试")
    class ConfigAccessTests {

        @Test
        @DisplayName("获取配置")
        void testGetConfig() {
            ContextConfig customConfig = new ContextConfig();
            customConfig.setMaxTokens(50000);
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, llmClient, customConfig
            );
            
            assertEquals(50000, manager.getConfig().getMaxTokens());
        }

        @Test
        @DisplayName("获取Compressor")
        void testGetCompressor() {
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, llmClient, config
            );
            
            assertNotNull(manager.getToolResultCompressor());
        }
    }

    @Nested
    @DisplayName("SystemPrompt切换测试")
    class SystemPromptTests {

        @Test
        @DisplayName("设置SystemPrompt不保留历史")
        void testSetSystemPromptNoPreserve() {
            ConversationManager manager = new ConversationManager("Old System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            
            manager.setSystemPrompt("New System", false);
            
            assertEquals(1, manager.getMessageCount());
            assertEquals("New System", manager.getHistory().get(0).getContent());
        }

        @Test
        @DisplayName("设置SystemPrompt保留历史")
        void testSetSystemPromptPreserveHistory() {
            ConversationManager manager = new ConversationManager("Old System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            
            manager.setSystemPrompt("New System", true);
            
            assertEquals(2, manager.getMessageCount());
            assertEquals("New System", manager.getHistory().get(0).getContent());
            assertEquals("Hello", manager.getHistory().get(1).getContent());
        }

        @Test
        @DisplayName("空历史时设置SystemPrompt重置")
        void testSetSystemPromptEmptyHistory() {
            ConversationManager manager = new ConversationManager("Old System", tokenEstimator, llmClient);
            
            manager.setSystemPrompt("New System", true);
            
            assertEquals(1, manager.getMessageCount());
            assertEquals("New System", manager.getHistory().get(0).getContent());
        }
    }

    @Nested
    @DisplayName("未完成工具调用修复测试")
    class UnfinishedToolCallFixTests {

        @Test
        @DisplayName("无未完成工具调用时不修改历史")
        void testNoUnfinishedToolCall() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            manager.addAssistantMessage(Message.assistant("Hi"));
            
            int countBefore = manager.getMessageCount();
            manager.fixUnfinishedToolCall();
            
            assertEquals(countBefore, manager.getMessageCount());
        }

        @Test
        @DisplayName("检测未完成的工具调用")
        void testDetectUnfinishedToolCall() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            manager.addUserMessage("Create a file");
            
            Message assistantMsg = Message.assistant("I will create the file");
            assistantMsg.addToolCall(new com.example.agent.llm.model.ToolCall(
                "call-1",
                new com.example.agent.llm.model.FunctionCall("create_file", "{}")
            ));
            manager.addAssistantMessage(assistantMsg);
            
            // 只是检测不崩溃，不做实际修改
            assertDoesNotThrow(() -> manager.fixUnfinishedToolCall());
        }
    }

    @Nested
    @DisplayName("会话导入导出测试")
    class SessionImportExportTests {

        @Test
        @DisplayName("导入null会话不崩溃")
        void testImportNullSession() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            
            assertDoesNotThrow(() -> manager.importSession(null));
        }

        @Test
        @DisplayName("导出会话")
        void testExportSession() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, llmClient);
            manager.addUserMessage("Hello");
            
            com.example.agent.session.SessionData data = manager.exportSession(
                "test-id", 
                com.example.agent.session.SessionData.Status.ACTIVE
            );
            
            assertNotNull(data);
            assertEquals("test-id", data.getSessionId());
            assertEquals(2, data.getMessages().size());
        }
    }
}
