package com.example.agent.context.compressor;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TruncateCompressorTest {

    private TokenEstimator tokenEstimator;
    private TruncateCompressor compressor;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        ContextConfig.ToolResultConfig config = new ContextConfig.ToolResultConfig();
        config.setMaxTokens(100);
        config.setTruncateStrategy("tail");
        compressor = new TruncateCompressor(tokenEstimator, config);
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("null消息返回原消息")
        void testNullMessage() {
            Message result = compressor.compress(null, 100);
            
            assertNull(result);
        }

        @Test
        @DisplayName("空内容消息返回原消息")
        void testEmptyContent() {
            Message msg = Message.toolResult("id", "tool", "");
            
            Message result = compressor.compress(msg, 100);
            
            assertEquals("", result.getContent());
        }

        @Test
        @DisplayName("null内容消息返回空字符串")
        void testNullContent() {
            Message msg = new Message();
            msg.setRole("tool");
            msg.setContent(null);
            
            Message result = compressor.compress(msg, 100);
            
            assertEquals("", result.getContent());
        }

        @Test
        @DisplayName("零maxTokens")
        void testZeroMaxTokens() {
            Message msg = Message.toolResult("id", "tool", "Some content");
            
            Message result = compressor.compress(msg, 0);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("负数maxTokens")
        void testNegativeMaxTokens() {
            Message msg = Message.toolResult("id", "tool", "Some content");
            
            Message result = compressor.compress(msg, -100);
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("supports方法测试")
    class SupportsTests {

        @Test
        @DisplayName("tool角色消息支持压缩")
        void testSupportsToolMessage() {
            Message msg = Message.toolResult("id", "tool", "result");
            
            assertTrue(compressor.supports(msg));
        }

        @Test
        @DisplayName("user角色消息不支持压缩")
        void testNotSupportsUserMessage() {
            Message msg = Message.user("Hello");
            
            assertFalse(compressor.supports(msg));
        }

        @Test
        @DisplayName("assistant角色消息不支持压缩")
        void testNotSupportsAssistantMessage() {
            Message msg = Message.assistant("Hi");
            
            assertFalse(compressor.supports(msg));
        }

        @Test
        @DisplayName("system角色消息不支持压缩")
        void testNotSupportsSystemMessage() {
            Message msg = Message.system("System");
            
            assertFalse(compressor.supports(msg));
        }

        @Test
        @DisplayName("null角色消息不支持压缩")
        void testNotSupportsNullRole() {
            Message msg = new Message();
            
            assertFalse(compressor.supports(msg));
        }
    }

    @Nested
    @DisplayName("压缩策略测试")
    class StrategyTests {

        @Test
        @DisplayName("tail策略-截断尾部")
        void testTailStrategy() {
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, 50, "tail");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a long text. ");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertTrue(result.getContent().contains("已截断"));
        }

        @Test
        @DisplayName("head策略-截断头部")
        void testHeadStrategy() {
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, 50, "head");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a long text. ");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertTrue(result.getContent().contains("已截断"));
        }

        @Test
        @DisplayName("smart策略-智能截断")
        void testSmartStrategy() {
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, 100, "smart");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a long text. ");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 100);
            
            assertTrue(result.getContent().contains("已截断"));
        }

        @Test
        @DisplayName("未知策略默认使用tail")
        void testUnknownStrategyDefaultsToTail() {
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, 50, "unknown");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a long text. ");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("策略大小写不敏感")
        void testStrategyCaseInsensitive() {
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, 50, "TAIL");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("This is a long text. ");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("压缩行为测试")
    class CompressionBehaviorTests {

        @Test
        @DisplayName("内容未超限不压缩")
        void testNoCompressionNeeded() {
            Message msg = Message.toolResult("id", "tool", "Short content");
            
            Message result = compressor.compress(msg, 1000);
            
            assertEquals("Short content", result.getContent());
        }

        @Test
        @DisplayName("压缩后保留角色")
        void testCompressedKeepsRole() {
            Message msg = Message.toolResult("id", "tool", "Some content here");
            
            Message result = compressor.compress(msg, 100);
            
            assertEquals("tool", result.getRole());
        }

        @Test
        @DisplayName("压缩后保留toolCallId")
        void testCompressedKeepsToolCallId() {
            Message msg = Message.toolResult("call-123", "bash", "result");
            
            Message result = compressor.compress(msg, 100);
            
            assertEquals("call-123", result.getToolCallId());
        }

        @Test
        @DisplayName("压缩后保留name")
        void testCompressedKeepsName() {
            Message msg = Message.toolResult("id", "bash", "result");
            
            Message result = compressor.compress(msg, 100);
            
            assertEquals("bash", result.getName());
        }

        @Test
        @DisplayName("压缩返回新消息对象")
        void testCompressedReturnsNewObject() {
            Message msg = Message.toolResult("id", "tool", "Some content");
            
            Message result = compressor.compress(msg, 100);
            
            assertNotSame(msg, result);
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("带ToolResultConfig构造")
        void testConstructorWithConfig() {
            ContextConfig.ToolResultConfig config = new ContextConfig.ToolResultConfig();
            config.setMaxTokens(500);
            config.setTruncateStrategy("smart");
            
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, config);
            
            assertEquals(500, compressor.getMaxTokens());
            assertEquals("smart", compressor.getStrategy());
        }

        @Test
        @DisplayName("直接指定参数构造")
        void testConstructorWithParams() {
            TruncateCompressor compressor = new TruncateCompressor(tokenEstimator, 300, "head");
            
            assertEquals(300, compressor.getMaxTokens());
            assertEquals("head", compressor.getStrategy());
        }
    }

    @Nested
    @DisplayName("特殊内容测试")
    class SpecialContentTests {

        @Test
        @DisplayName("纯中文内容压缩")
        void testChineseContent() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                sb.append("这是一段中文测试内容。");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertNotNull(result.getContent());
        }

        @Test
        @DisplayName("包含换行符的内容")
        void testContentWithNewlines() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("Line ").append(i).append("\n");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertNotNull(result.getContent());
        }

        @Test
        @DisplayName("包含特殊字符的内容")
        void testContentWithSpecialChars() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("Special: !@#$%^&*(){}[]|\\;':\",./<>?`~");
            }
            Message msg = Message.toolResult("id", "tool", sb.toString());
            
            Message result = compressor.compress(msg, 50);
            
            assertNotNull(result.getContent());
        }

        @Test
        @DisplayName("超短内容不压缩")
        void testVeryShortContent() {
            Message msg = Message.toolResult("id", "tool", "OK");
            
            Message result = compressor.compress(msg, 100);
            
            assertEquals("OK", result.getContent());
        }
    }

    @Nested
    @DisplayName("Getter方法测试")
    class GetterTests {

        @Test
        @DisplayName("getMaxTokens")
        void testGetMaxTokens() {
            assertEquals(100, compressor.getMaxTokens());
        }

        @Test
        @DisplayName("getStrategy")
        void testGetStrategy() {
            assertEquals("tail", compressor.getStrategy());
        }
    }
}
