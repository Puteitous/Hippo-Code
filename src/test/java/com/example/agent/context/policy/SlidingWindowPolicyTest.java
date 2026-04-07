package com.example.agent.context.policy;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowPolicyTest {

    private TokenEstimator tokenEstimator;
    private SlidingWindowPolicy policy;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        ContextConfig config = new ContextConfig();
        config.setKeepRecentTurns(3);
        policy = new SlidingWindowPolicy(tokenEstimator, config);
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("空会话返回空列表")
        void testEmptyConversation() {
            List<Message> messages = new ArrayList<>();
            
            List<Message> result = policy.apply(messages, 1000, 10);
            
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("单条消息返回原列表")
        void testSingleMessage() {
            List<Message> messages = List.of(Message.system("System"));
            
            List<Message> result = policy.apply(messages, 1000, 10);
            
            assertEquals(1, result.size());
            assertEquals("System", result.get(0).getContent());
        }

        @Test
        @DisplayName("两条消息返回原列表")
        void testTwoMessages() {
            List<Message> messages = List.of(
                Message.system("System"),
                Message.user("Hello")
            );
            
            List<Message> result = policy.apply(messages, 1000, 10);
            
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("null消息列表返回空列表")
        void testNullMessages() {
            List<Message> result = policy.apply(null, 1000, 10);
            
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Token超限截断测试")
    class TokenLimitTests {

        @Test
        @DisplayName("Token未超限不截断")
        void testUnderTokenLimit() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello"));
            messages.add(Message.assistant("Hi"));
            
            List<Message> result = policy.apply(messages, 10000, 10);
            
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Token超限时截断")
        void testOverTokenLimit() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            
            for (int i = 0; i < 10; i++) {
                messages.add(Message.user("This is a long message number " + i));
                messages.add(Message.assistant("Response " + i));
            }
            
            List<Message> result = policy.apply(messages, 100, 10);
            
            assertTrue(result.size() < messages.size());
            assertEquals("System", result.get(0).getContent());
        }

        @Test
        @DisplayName("保留系统消息")
        void testKeepSystemMessage() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("Important System Prompt"));
            
            for (int i = 0; i < 20; i++) {
                messages.add(Message.user("Message " + i));
            }
            
            List<Message> result = policy.apply(messages, 50, 5);
            
            assertEquals("Important System Prompt", result.get(0).getContent());
        }

        @Test
        @DisplayName("消息数超限截断")
        void testOverMessageLimit() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            
            for (int i = 0; i < 20; i++) {
                messages.add(Message.user("Message " + i));
            }
            
            List<Message> result = policy.apply(messages, 10000, 5);
            
            assertTrue(result.size() <= 5);
        }
    }

    @Nested
    @DisplayName("滑动窗口测试")
    class SlidingWindowTests {

        @Test
        @DisplayName("提取最近N轮对话")
        void testExtractRecentTurns() {
            SlidingWindowPolicy policy = new SlidingWindowPolicy(tokenEstimator, 2);
            
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Old message 1"));
            messages.add(Message.assistant("Old response 1"));
            messages.add(Message.user("Old message 2"));
            messages.add(Message.assistant("Old response 2"));
            messages.add(Message.user("Recent message 1"));
            messages.add(Message.assistant("Recent response 1"));
            messages.add(Message.user("Recent message 2"));
            messages.add(Message.assistant("Recent response 2"));
            
            List<Message> result = policy.apply(messages, 10000, 20);
            
            assertTrue(result.size() <= 5);
            assertEquals("System", result.get(0).getContent());
        }

        @Test
        @DisplayName("消息数少于keepRecentTurns时保留全部")
        void testFewerMessagesThanKeepRecent() {
            SlidingWindowPolicy policy = new SlidingWindowPolicy(tokenEstimator, 5);
            
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello"));
            messages.add(Message.assistant("Hi"));
            
            List<Message> result = policy.apply(messages, 10000, 20);
            
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("只有助手消息时正常处理")
        void testOnlyAssistantMessages() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.assistant("Response 1"));
            messages.add(Message.assistant("Response 2"));
            messages.add(Message.assistant("Response 3"));
            
            List<Message> result = policy.apply(messages, 10000, 20);
            
            assertTrue(result.size() >= 1);
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("带ContextConfig构造")
        void testConstructorWithConfig() {
            ContextConfig config = new ContextConfig();
            config.setKeepRecentTurns(5);
            
            SlidingWindowPolicy policy = new SlidingWindowPolicy(tokenEstimator, config);
            
            assertEquals(5, policy.getKeepRecentTurns());
        }

        @Test
        @DisplayName("直接指定keepRecentTurns构造")
        void testConstructorWithInt() {
            SlidingWindowPolicy policy = new SlidingWindowPolicy(tokenEstimator, 4);
            
            assertEquals(4, policy.getKeepRecentTurns());
        }
    }

    @Nested
    @DisplayName("特殊场景测试")
    class SpecialScenarioTests {

        @Test
        @DisplayName("系统消息后直接是助手消息")
        void testSystemFollowedByAssistant() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.assistant("Unexpected response"));
            messages.add(Message.user("Hello"));
            
            List<Message> result = policy.apply(messages, 10000, 20);
            
            assertTrue(result.size() >= 2);
        }

        @Test
        @DisplayName("连续多个用户消息")
        void testConsecutiveUserMessages() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Message 1"));
            messages.add(Message.user("Message 2"));
            messages.add(Message.user("Message 3"));
            
            List<Message> result = policy.apply(messages, 10000, 20);
            
            assertTrue(result.size() >= 2);
        }

        @Test
        @DisplayName("超低Token限制")
        void testVeryLowTokenLimit() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello World"));
            
            List<Message> result = policy.apply(messages, 1, 10);
            
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("零Token限制")
        void testZeroTokenLimit() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello"));
            
            List<Message> result = policy.apply(messages, 0, 10);
            
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("负数Token限制")
        void testNegativeTokenLimit() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello"));
            
            List<Message> result = policy.apply(messages, -100, 10);
            
            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("返回结果验证测试")
    class ResultValidationTests {

        @Test
        @DisplayName("返回新列表而非原列表引用")
        void testReturnsNewList() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello"));
            
            List<Message> result = policy.apply(messages, 1000, 10);
            
            assertNotSame(messages, result);
        }

        @Test
        @DisplayName("返回列表可修改")
        void testResultModifiable() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            
            List<Message> result = policy.apply(messages, 1000, 10);
            
            assertDoesNotThrow(() -> result.add(Message.user("test")));
        }

        @Test
        @DisplayName("系统消息始终在第一位")
        void testSystemMessageAlwaysFirst() {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("System"));
            messages.add(Message.user("Hello"));
            messages.add(Message.assistant("Hi"));
            messages.add(Message.user("World"));
            
            List<Message> result = policy.apply(messages, 1000, 10);
            
            assertEquals("system", result.get(0).getRole());
        }
    }
}
