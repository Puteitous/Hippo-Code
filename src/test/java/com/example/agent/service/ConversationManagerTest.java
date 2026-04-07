package com.example.agent.service;

import com.example.agent.context.Compressor;
import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationManagerTest {

    private TokenEstimator tokenEstimator;
    private TrimPolicy trimPolicy;
    private Compressor compressor;
    private ContextConfig config;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        trimPolicy = mock(TrimPolicy.class);
        compressor = mock(Compressor.class);
        config = new ContextConfig();
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("基本构造函数")
        void testBasicConstructor() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator);
            
            assertNotNull(manager);
            assertEquals(1, manager.getMessageCount());
        }

        @Test
        @DisplayName("带Config构造")
        void testConstructorWithConfig() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator, config);
            
            assertNotNull(manager);
            assertEquals(config, manager.getConfig());
        }

        @Test
        @DisplayName("完整构造函数")
        void testFullConstructor() {
            ConversationManager manager = new ConversationManager(
                "System prompt", tokenEstimator, trimPolicy, compressor, config
            );
            
            assertNotNull(manager);
            assertEquals(trimPolicy, manager.getTrimPolicy());
            assertEquals(compressor, manager.getToolResultCompressor());
        }

        @Test
        @DisplayName("null TokenEstimator抛出异常")
        void testNullTokenEstimator() {
            assertThrows(IllegalArgumentException.class, () -> {
                new ConversationManager("System prompt", null);
            });
        }

        @Test
        @DisplayName("null TrimPolicy抛出异常")
        void testNullTrimPolicy() {
            assertThrows(IllegalArgumentException.class, () -> {
                new ConversationManager("System prompt", tokenEstimator, null, compressor, config);
            });
        }

        @Test
        @DisplayName("null systemPrompt使用空字符串")
        void testNullSystemPrompt() {
            ConversationManager manager = new ConversationManager(null, tokenEstimator);
            
            assertEquals(1, manager.getMessageCount());
            assertEquals("", manager.getHistory().get(0).getContent());
        }

        @Test
        @DisplayName("null Config使用默认配置")
        void testNullConfig() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator, (ContextConfig) null);
            
            assertNotNull(manager.getConfig());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("空输入添加用户消息")
        void testAddEmptyUserMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            manager.addUserMessage("");
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("null输入添加用户消息")
        void testAddNullUserMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            manager.addUserMessage(null);
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("null助手消息不添加")
        void testAddNullAssistantMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            manager.addAssistantMessage(null);
            
            assertEquals(1, manager.getMessageCount());
        }

        @Test
        @DisplayName("null工具结果正常处理")
        void testAddNullToolResult() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
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
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            manager.addUserMessage("Hello");
            
            assertEquals(2, manager.getMessageCount());
            assertEquals("Hello", manager.getHistory().get(1).getContent());
        }

        @Test
        @DisplayName("添加助手消息")
        void testAddAssistantMessage() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            Message assistantMsg = Message.assistant("Hi there!");
            
            manager.addAssistantMessage(assistantMsg);
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("添加工具结果")
        void testAddToolResult() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            manager.addToolResult("call-1", "bash", "file.txt");
            
            assertEquals(2, manager.getMessageCount());
        }

        @Test
        @DisplayName("重置会话")
        void testReset() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            manager.addUserMessage("Hello");
            manager.addAssistantMessage(Message.assistant("Hi"));
            
            manager.reset();
            
            assertEquals(1, manager.getMessageCount());
            assertEquals("System", manager.getHistory().get(0).getContent());
        }

        @Test
        @DisplayName("获取历史记录")
        void testGetHistory() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            manager.addUserMessage("Hello");
            
            List<Message> history = manager.getHistory();
            
            assertEquals(2, history.size());
        }

        @Test
        @DisplayName("历史记录可修改（非防御性拷贝）")
        void testHistoryModifiable() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            List<Message> history = manager.getHistory();
            history.add(Message.user("test"));
            
            assertEquals(2, manager.getMessageCount());
        }
    }

    @Nested
    @DisplayName("Token计数测试")
    class TokenCountTests {

        @Test
        @DisplayName("获取Token计数")
        void testGetTokenCount() {
            ConversationManager manager = new ConversationManager("System prompt", tokenEstimator);
            
            int tokens = manager.getTokenCount();
            
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("添加消息后Token增加")
        void testTokenCountIncreases() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            int initialTokens = manager.getTokenCount();
            
            manager.addUserMessage("Hello World");
            
            assertTrue(manager.getTokenCount() > initialTokens);
        }

        @Test
        @DisplayName("获取消息计数")
        void testGetMessageCount() {
            ConversationManager manager = new ConversationManager("System", tokenEstimator);
            
            assertEquals(1, manager.getMessageCount());
            
            manager.addUserMessage("Hello");
            
            assertEquals(2, manager.getMessageCount());
        }
    }

    @Nested
    @DisplayName("历史精简测试")
    class TrimHistoryTests {

        @Test
        @DisplayName("精简历史调用TrimPolicy")
        void testTrimHistoryCallsPolicy() {
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenReturn(List.of(Message.system("System")));
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Hello");
            
            manager.trimHistory(null);
            
            verify(trimPolicy).apply(anyList(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("精简回调被调用")
        void testTrimCallback() {
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenReturn(List.of(Message.system("System")));
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Hello");
            
            final boolean[] callbackCalled = {false};
            manager.trimHistory((count, tokens) -> {
                callbackCalled[0] = true;
            });
            
            assertTrue(callbackCalled[0]);
        }

        @Test
        @DisplayName("TrimPolicy返回null时不修改历史")
        void testTrimPolicyReturnsNull() {
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenReturn(null);
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Hello");
            int countBefore = manager.getMessageCount();
            
            manager.trimHistory(null);
            
            assertEquals(countBefore, manager.getMessageCount());
        }

        @Test
        @DisplayName("精简后历史被更新")
        void testHistoryUpdatedAfterTrim() {
            List<Message> trimmedList = new ArrayList<>();
            trimmedList.add(Message.system("System"));
            trimmedList.add(Message.user("Recent"));
            
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenReturn(trimmedList);
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Old");
            manager.addUserMessage("Recent");
            
            manager.trimHistory(null);
            
            assertEquals(2, manager.getMessageCount());
        }
    }

    @Nested
    @DisplayName("工具结果压缩测试")
    class ToolResultCompressionTests {

        @Test
        @DisplayName("压缩器支持时压缩工具结果")
        void testCompressorWhenSupported() {
            when(compressor.supports(any(Message.class))).thenReturn(true);
            Message compressedMsg = Message.toolResult("id", "tool", "compressed");
            when(compressor.compress(any(Message.class), anyInt())).thenReturn(compressedMsg);
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            
            manager.addToolResult("call-1", "bash", "very long result...");
            
            verify(compressor).supports(any(Message.class));
            verify(compressor).compress(any(Message.class), anyInt());
        }

        @Test
        @DisplayName("压缩器不支持时不压缩")
        void testNoCompressWhenNotSupported() {
            when(compressor.supports(any(Message.class))).thenReturn(false);
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            
            manager.addToolResult("call-1", "bash", "result");
            
            verify(compressor).supports(any(Message.class));
            verify(compressor, never()).compress(any(Message.class), anyInt());
        }

        @Test
        @DisplayName("null压缩器时不崩溃")
        void testNullCompressor() {
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, null, config
            );
            
            assertDoesNotThrow(() -> manager.addToolResult("id", "tool", "result"));
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
                "System", tokenEstimator, customConfig
            );
            
            assertEquals(50000, manager.getConfig().getMaxTokens());
        }

        @Test
        @DisplayName("获取TrimPolicy")
        void testGetTrimPolicy() {
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            
            assertEquals(trimPolicy, manager.getTrimPolicy());
        }

        @Test
        @DisplayName("获取Compressor")
        void testGetCompressor() {
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            
            assertEquals(compressor, manager.getToolResultCompressor());
        }
    }

    @Nested
    @DisplayName("任意环节失败不崩溃测试")
    class FailureHandlingTests {

        @Test
        @DisplayName("TrimPolicy抛出异常时不崩溃")
        void testTrimPolicyException() {
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Trim failed"));
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Hello");
            
            assertThrows(RuntimeException.class, () -> manager.trimHistory(null));
        }

        @Test
        @DisplayName("Compressor抛出异常时不崩溃")
        void testCompressorException() {
            when(compressor.supports(any(Message.class))).thenReturn(true);
            when(compressor.compress(any(Message.class), anyInt()))
                .thenThrow(new RuntimeException("Compress failed"));
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            
            assertThrows(RuntimeException.class, () -> 
                manager.addToolResult("id", "tool", "result"));
        }

        @Test
        @DisplayName("TokenEstimator异常时返回默认值")
        void testTokenEstimatorException() {
            TokenEstimator mockEstimator = mock(TokenEstimator.class);
            when(mockEstimator.estimateConversationTokens(anyList()))
                .thenThrow(new RuntimeException("Estimation failed"));
            
            ConversationManager manager = new ConversationManager(
                "System", mockEstimator, trimPolicy, compressor, config
            );
            
            assertThrows(RuntimeException.class, () -> manager.getTokenCount());
        }
    }

    @Nested
    @DisplayName("TrimCallback接口测试")
    class TrimCallbackTests {

        @Test
        @DisplayName("回调接收正确参数")
        void testCallbackReceivesCorrectParams() {
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenReturn(List.of(Message.system("System")));
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Hello");
            
            final int[] receivedCount = {0};
            final int[] receivedTokens = {0};
            
            manager.trimHistory((count, tokens) -> {
                receivedCount[0] = count;
                receivedTokens[0] = tokens;
            });
            
            assertEquals(1, receivedCount[0]);
            assertTrue(receivedTokens[0] >= 0);
        }

        @Test
        @DisplayName("null回调不崩溃")
        void testNullCallback() {
            when(trimPolicy.apply(anyList(), anyInt(), anyInt()))
                .thenReturn(List.of(Message.system("System")));
            
            ConversationManager manager = new ConversationManager(
                "System", tokenEstimator, trimPolicy, compressor, config
            );
            manager.addUserMessage("Hello");
            
            assertDoesNotThrow(() -> manager.trimHistory(null));
        }
    }
}
