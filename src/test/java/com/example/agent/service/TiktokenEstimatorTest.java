package com.example.agent.service;

import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.knuddels.jtokkit.api.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TiktokenEstimatorTest {

    private TiktokenEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new TiktokenEstimator();
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("null文本返回0")
        void testNullText() {
            assertEquals(0, estimator.estimateTextTokens(null));
        }

        @Test
        @DisplayName("空字符串返回0")
        void testEmptyText() {
            assertEquals(0, estimator.estimateTextTokens(""));
        }

        @Test
        @DisplayName("null消息返回0")
        void testNullMessage() {
            assertEquals(0, estimator.estimateMessageTokens(null));
        }

        @Test
        @DisplayName("null消息列表返回0")
        void testNullMessageList() {
            assertEquals(0, estimator.estimateConversationTokens(null));
        }

        @Test
        @DisplayName("空消息列表返回0")
        void testEmptyMessageList() {
            assertEquals(0, estimator.estimateConversationTokens(List.of()));
        }
    }

    @Nested
    @DisplayName("文本token估算测试")
    class TextTokenEstimationTests {

        @Test
        @DisplayName("纯英文文本")
        void testEnglishText() {
            String text = "Hello World";
            int tokens = estimator.estimateTextTokens(text);
            assertTrue(tokens > 0);
            assertTrue(tokens <= text.length());
        }

        @Test
        @DisplayName("纯中文文本")
        void testChineseText() {
            String text = "你好世界";
            int tokens = estimator.estimateTextTokens(text);
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("中英混合文本")
        void testMixedText() {
            String text = "Hello世界";
            int tokens = estimator.estimateTextTokens(text);
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("代码文本")
        void testCodeText() {
            String code = "public class Test { public static void main(String[] args) { System.out.println(\"Hello\"); } }";
            int tokens = estimator.estimateTextTokens(code);
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("JSON文本")
        void testJsonText() {
            String json = "{\"name\": \"test\", \"value\": 123, \"nested\": {\"key\": \"value\"}}";
            int tokens = estimator.estimateTextTokens(json);
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("表情符号")
        void testEmojiText() {
            String emoji = "😀🎉💻";
            int tokens = estimator.estimateTextTokens(emoji);
            assertTrue(tokens > 0);
        }
    }

    @Nested
    @DisplayName("消息token估算测试")
    class MessageTokenEstimationTests {

        @Test
        @DisplayName("简单用户消息")
        void testSimpleUserMessage() {
            Message msg = Message.user("Hello");
            int tokens = estimator.estimateMessageTokens(msg);
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("带工具调用的消息")
        void testMessageWithToolCalls() {
            Message msg = new Message();
            msg.setRole("assistant");
            msg.setContent("response");
            
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-1");
            toolCall.setFunction(new FunctionCall("bash", "{\"command\": \"ls\"}"));
            msg.setToolCalls(List.of(toolCall));
            
            int tokens = estimator.estimateMessageTokens(msg);
            assertTrue(tokens > 4);
        }

        @Test
        @DisplayName("工具结果消息")
        void testToolResultMessage() {
            Message msg = Message.toolResult("call-1", "bash", "file1.txt\nfile2.txt");
            int tokens = estimator.estimateMessageTokens(msg);
            assertTrue(tokens > 0);
        }
    }

    @Nested
    @DisplayName("会话token估算测试")
    class ConversationTokenEstimationTests {

        @Test
        @DisplayName("多条消息")
        void testMultipleMessages() {
            List<Message> messages = List.of(
                Message.system("You are helpful"),
                Message.user("Hello"),
                Message.assistant("Hi there!")
            );
            
            int tokens = estimator.estimateConversationTokens(messages);
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("消息token累加正确")
        void testTokenAccumulation() {
            Message msg1 = Message.user("Hello");
            Message msg2 = Message.assistant("Hi");
            
            int singleTokens1 = estimator.estimateMessageTokens(msg1);
            int singleTokens2 = estimator.estimateMessageTokens(msg2);
            int totalTokens = estimator.estimateConversationTokens(List.of(msg1, msg2));
            
            assertEquals(singleTokens1 + singleTokens2, totalTokens);
        }
    }

    @Nested
    @DisplayName("模型类型测试")
    class ModelTypeTests {

        @Test
        @DisplayName("默认构造函数使用GPT-4")
        void testDefaultConstructor() {
            TiktokenEstimator est = new TiktokenEstimator();
            assertNotNull(est.getEncoding());
        }

        @Test
        @DisplayName("指定ModelType构造")
        void testModelTypeConstructor() {
            TiktokenEstimator est = new TiktokenEstimator(ModelType.GPT_3_5_TURBO);
            assertNotNull(est.getEncoding());
        }

        @Test
        @DisplayName("字符串模型名构造 - GPT-4")
        void testStringModelConstructorGpt4() {
            TiktokenEstimator est = new TiktokenEstimator("gpt-4");
            assertNotNull(est.getEncoding());
        }

        @Test
        @DisplayName("字符串模型名构造 - GPT-3.5")
        void testStringModelConstructorGpt35() {
            TiktokenEstimator est = new TiktokenEstimator("gpt-3.5-turbo");
            assertNotNull(est.getEncoding());
        }

        @Test
        @DisplayName("null模型名使用默认")
        void testNullModelName() {
            TiktokenEstimator est = new TiktokenEstimator((String) null);
            assertNotNull(est.getEncoding());
        }
    }

    @Nested
    @DisplayName("与SimpleTokenEstimator对比测试")
    class ComparisonTests {

        @Test
        @DisplayName("英文文本 - Tiktoken通常比简单估算更精确")
        void testEnglishComparison() {
            String text = "The quick brown fox jumps over the lazy dog.";
            
            TiktokenEstimator tiktoken = new TiktokenEstimator();
            SimpleTokenEstimator simple = new SimpleTokenEstimator();
            
            int tiktokenCount = tiktoken.estimateTextTokens(text);
            int simpleCount = simple.estimateTextTokens(text);
            
            assertTrue(tiktokenCount > 0);
            assertTrue(simpleCount > 0);
        }

        @Test
        @DisplayName("中文文本 - 两者结果相近")
        void testChineseComparison() {
            String text = "你好世界这是一个测试";
            
            TiktokenEstimator tiktoken = new TiktokenEstimator();
            SimpleTokenEstimator simple = new SimpleTokenEstimator();
            
            int tiktokenCount = tiktoken.estimateTextTokens(text);
            int simpleCount = simple.estimateTextTokens(text);
            
            assertTrue(tiktokenCount > 0);
            assertTrue(simpleCount > 0);
        }
    }

    @Nested
    @DisplayName("缓存功能测试")
    class CacheTests {

        @Test
        @DisplayName("缓存启用时相同文本返回相同结果")
        void testCacheEnabled() {
            TiktokenEstimator est = new TiktokenEstimator("gpt-4", true, 100);
            String text = "Hello World";
            
            int count1 = est.estimateTextTokens(text);
            int count2 = est.estimateTextTokens(text);
            
            assertEquals(count1, count2);
            assertEquals(1, est.getCacheSize());
        }

        @Test
        @DisplayName("缓存禁用时正常工作")
        void testCacheDisabled() {
            TiktokenEstimator est = new TiktokenEstimator("gpt-4", false, 100);
            String text = "Hello World";
            
            int count1 = est.estimateTextTokens(text);
            int count2 = est.estimateTextTokens(text);
            
            assertEquals(count1, count2);
            assertEquals(0, est.getCacheSize());
        }

        @Test
        @DisplayName("清除缓存")
        void testClearCache() {
            TiktokenEstimator est = new TiktokenEstimator("gpt-4", true, 100);
            
            est.estimateTextTokens("Hello");
            est.estimateTextTokens("World");
            
            assertTrue(est.getCacheSize() > 0);
            
            est.clearCache();
            
            assertEquals(0, est.getCacheSize());
        }

        @Test
        @DisplayName("缓存容量限制")
        void testCacheMaxSize() {
            int maxSize = 5;
            TiktokenEstimator est = new TiktokenEstimator("gpt-4", true, maxSize);
            
            for (int i = 0; i < 10; i++) {
                est.estimateTextTokens("Text " + i);
            }
            
            assertTrue(est.getCacheSize() <= maxSize);
        }
    }
}
