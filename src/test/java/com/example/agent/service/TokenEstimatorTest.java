package com.example.agent.service;

import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimatorTest {

    private TokenEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new SimpleTokenEstimator();
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
        @DisplayName("null消息列表返回0")
        void testNullMessageList() {
            assertEquals(0, estimator.estimateConversationTokens(null));
        }

        @Test
        @DisplayName("空消息列表返回0")
        void testEmptyMessageList() {
            assertEquals(0, estimator.estimateConversationTokens(new ArrayList<>()));
        }

        @Test
        @DisplayName("null消息返回0")
        void testNullMessage() {
            assertEquals(0, estimator.estimateMessageTokens(null));
        }

        @Test
        @DisplayName("包含null消息的列表正常处理")
        void testMessageListWithNulls() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.user("test"));
            messages.add(null);
            messages.add(Message.assistant("response"));
            
            int tokens = estimator.estimateConversationTokens(messages);
            
            assertTrue(tokens > 0);
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
            
            assertEquals(4, tokens);
        }

        @Test
        @DisplayName("中英混合文本")
        void testMixedText() {
            String text = "Hello世界";
            
            int tokens = estimator.estimateTextTokens(text);
            
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("特殊字符文本")
        void testSpecialCharacters() {
            String text = "!@#$%^&*()";
            
            int tokens = estimator.estimateTextTokens(text);
            
            assertTrue(tokens >= 0);
        }

        @Test
        @DisplayName("超长文本")
        void testVeryLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("a");
            }
            
            int tokens = estimator.estimateTextTokens(sb.toString());
            
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("空白字符文本")
        void testWhitespaceText() {
            int tokens = estimator.estimateTextTokens("   \t\n  ");
            
            assertTrue(tokens >= 0);
        }

        @Test
        @DisplayName("Unicode表情符号")
        void testEmojiText() {
            int tokens = estimator.estimateTextTokens("😀🎉💻");
            
            assertTrue(tokens >= 0);
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
        @DisplayName("null内容的消息")
        void testMessageWithNullContent() {
            Message msg = new Message();
            msg.setRole("user");
            
            int tokens = estimator.estimateMessageTokens(msg);
            
            assertEquals(4, tokens);
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
        @DisplayName("工具调用为null的消息")
        void testMessageWithNullToolCalls() {
            Message msg = new Message();
            msg.setRole("assistant");
            msg.setContent("response");
            msg.setToolCalls(null);
            
            int tokens = estimator.estimateMessageTokens(msg);
            
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("工具调用包含null的消息")
        void testMessageWithNullToolCallElements() {
            Message msg = new Message();
            msg.setRole("assistant");
            
            List<ToolCall> toolCalls = new ArrayList<>();
            toolCalls.add(null);
            ToolCall validCall = new ToolCall();
            validCall.setFunction(new FunctionCall("test", "{}"));
            toolCalls.add(validCall);
            msg.setToolCalls(toolCalls);
            
            int tokens = estimator.estimateMessageTokens(msg);
            
            assertTrue(tokens > 0);
        }

        @Test
        @DisplayName("工具调用function为null的消息")
        void testMessageWithNullFunction() {
            Message msg = new Message();
            msg.setRole("assistant");
            
            ToolCall toolCall = new ToolCall();
            toolCall.setFunction(null);
            msg.setToolCalls(List.of(toolCall));
            
            int tokens = estimator.estimateMessageTokens(msg);
            
            assertEquals(4, tokens);
        }

        @Test
        @DisplayName("工具调用arguments为null的消息")
        void testMessageWithNullArguments() {
            Message msg = new Message();
            msg.setRole("assistant");
            
            ToolCall toolCall = new ToolCall();
            toolCall.setFunction(new FunctionCall("test", null));
            msg.setToolCalls(List.of(toolCall));
            
            int tokens = estimator.estimateMessageTokens(msg);
            
            assertEquals(4, tokens);
        }
    }

    @Nested
    @DisplayName("会话token估算测试")
    class ConversationTokenEstimationTests {

        @Test
        @DisplayName("单条消息")
        void testSingleMessage() {
            List<Message> messages = List.of(Message.user("Hello"));
            
            int tokens = estimator.estimateConversationTokens(messages);
            
            assertTrue(tokens > 0);
        }

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
        @DisplayName("大量消息")
        void testManyMessages() {
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                messages.add(Message.user("Message " + i));
            }
            
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
    @DisplayName("估算准确性测试")
    class EstimationAccuracyTests {

        @Test
        @DisplayName("英文估算比例约为1:4")
        void testEnglishRatio() {
            String text = "Hello World Test";
            int length = text.length();
            
            int tokens = estimator.estimateTextTokens(text);
            
            assertEquals(length / 4, tokens);
        }

        @Test
        @DisplayName("中文估算比例为1:1")
        void testChineseRatio() {
            String text = "你好世界测试";
            int length = text.length();
            
            int tokens = estimator.estimateTextTokens(text);
            
            assertEquals(length, tokens);
        }

        @Test
        @DisplayName("混合文本估算")
        void testMixedRatio() {
            String text = "Hello世界Test测试";
            
            int tokens = estimator.estimateTextTokens(text);
            
            assertTrue(tokens > 0);
        }
    }
}
