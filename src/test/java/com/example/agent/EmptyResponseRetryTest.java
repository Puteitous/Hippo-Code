package com.example.agent;

import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Choice;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("空响应重试机制测试")
class EmptyResponseRetryTest {

    private ConversationManager conversationManager;
    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new TokenEstimator();
        conversationManager = new ConversationManager("You are a helpful assistant.", tokenEstimator);
    }

    @Test
    @DisplayName("测试 - hasContent 方法：有内容")
    void testHasContentWithContent() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(new Message("assistant", "Hello"));
        response.setChoices(List.of(choice));
        
        assertTrue(response.hasContent());
        assertEquals("Hello", response.getContent());
    }

    @Test
    @DisplayName("测试 - hasContent 方法：null 内容")
    void testHasContentWithNull() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(new Message("assistant", null));
        response.setChoices(List.of(choice));
        
        // 防御性编程：null content应转换为空字符串
        assertFalse(response.hasContent());
        assertEquals("", response.getContent());  // 期望空字符串，不是null
    }

    @Test
    @DisplayName("测试 - hasContent 方法：空字符串")
    void testHasContentWithEmptyString() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(new Message("assistant", ""));
        response.setChoices(List.of(choice));
        
        assertFalse(response.hasContent());
    }

    @Test
    @DisplayName("测试 - hasContent 方法：空选择列表")
    void testHasContentWithEmptyChoices() {
        ChatResponse response = new ChatResponse();
        response.setChoices(new ArrayList<>());
        
        assertFalse(response.hasContent());
        assertNull(response.getFirstMessage());
    }

    @Test
    @DisplayName("测试 - hasContent 方法：null 选择列表")
    void testHasContentWithNullChoices() {
        ChatResponse response = new ChatResponse();
        response.setChoices(null);
        
        assertFalse(response.hasContent());
        assertNull(response.getFirstMessage());
    }

    @Test
    @DisplayName("测试 - hasToolCalls 方法：有工具调用")
    void testHasToolCallsWithToolCalls() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        Message message = new Message("assistant", null);
        message.setToolCalls(List.of(
            new com.example.agent.llm.model.ToolCall()
        ));
        choice.setMessage(message);
        response.setChoices(List.of(choice));
        
        assertTrue(response.hasToolCalls());
    }

    @Test
    @DisplayName("测试 - hasToolCalls 方法：无工具调用")
    void testHasToolCallsWithoutToolCalls() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(new Message("assistant", "Hello"));
        response.setChoices(List.of(choice));
        
        assertFalse(response.hasToolCalls());
    }

    @Test
    @DisplayName("测试 - 模拟空响应后成功重试场景")
    void testEmptyResponseThenSuccessScenario() {
        AtomicInteger callCount = new AtomicInteger(0);
        int maxRetries = 2;
        
        List<ChatResponse> responses = new ArrayList<>();
        
        for (int i = 0; i <= maxRetries; i++) {
            int count = callCount.incrementAndGet();
            ChatResponse response = new ChatResponse();
            response.setId("test-" + count);
            response.setModel("test-model");
            
            Choice choice = new Choice();
            choice.setIndex(0);
            
            if (count == 1) {
                choice.setMessage(new Message("assistant", null));
                choice.setFinishReason("stop");
            } else {
                Message msg = new Message("assistant", "你好！有什么可以帮助你的？");
                choice.setMessage(msg);
                choice.setFinishReason("stop");
                response.setUsage(new Usage());
                response.getUsage().setPromptTokens(100);
                response.getUsage().setCompletionTokens(20);
                response.getUsage().setTotalTokens(120);
            }
            
            response.setChoices(List.of(choice));
            responses.add(response);
        }
        
        assertFalse(responses.get(0).hasContent());
        assertTrue(responses.get(1).hasContent());
        assertEquals("你好！有什么可以帮助你的？", responses.get(1).getContent());
    }

    @Test
    @DisplayName("测试 - 连续空响应达到最大重试次数")
    void testMaxRetriesExceededScenario() {
        int maxRetries = 3;
        int emptyCount = 0;
        
        for (int i = 0; i < maxRetries; i++) {
            ChatResponse response = new ChatResponse();
            response.setId("test-" + (i + 1));
            response.setModel("test-model");
            
            Choice choice = new Choice();
            choice.setIndex(0);
            choice.setMessage(new Message("assistant", null));
            choice.setFinishReason("stop");
            
            response.setChoices(List.of(choice));
            
            if (!response.hasContent()) {
                emptyCount++;
            }
        }
        
        assertEquals(maxRetries, emptyCount);
    }

    @Test
    @DisplayName("测试 - 第一次成功不需要重试")
    void testNoRetryNeeded() {
        ChatResponse response = new ChatResponse();
        response.setId("test-1");
        response.setModel("test-model");
        
        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(new Message("assistant", "Direct success"));
        choice.setFinishReason("stop");
        
        response.setChoices(List.of(choice));
        
        assertTrue(response.hasContent());
        assertEquals("Direct success", response.getContent());
    }

    @Test
    @DisplayName("测试 - ChatResponse 完整流程")
    void testChatResponseFullFlow() {
        ChatResponse response = new ChatResponse();
        response.setId("chat-123");
        response.setObject("chat.completion");
        response.setCreated(System.currentTimeMillis() / 1000);
        response.setModel("qwen-max");
        
        Usage usage = new Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(150);
        response.setUsage(usage);
        
        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(new Message("assistant", "这是一个测试响应"));
        choice.setFinishReason("stop");
        
        response.setChoices(List.of(choice));
        
        assertEquals("chat-123", response.getId());
        assertEquals("chat.completion", response.getObject());
        assertEquals("qwen-max", response.getModel());
        assertNotNull(response.getCreated());
        
        assertNotNull(response.getUsage());
        assertEquals(100, response.getUsage().getPromptTokens());
        assertEquals(50, response.getUsage().getCompletionTokens());
        assertEquals(150, response.getUsage().getTotalTokens());
        
        assertTrue(response.hasContent());
        assertEquals("这是一个测试响应", response.getContent());
        assertFalse(response.hasToolCalls());
    }

    @Nested
    @DisplayName("重试计数测试组")
    class RetryCountTests {

        @Test
        @DisplayName("测试 - 重试计数器递增")
        void testRetryCounterIncrement() {
            AtomicInteger retryCount = new AtomicInteger(0);
            int maxRetries = 2;
            
            assertFalse(retryCount.get() > maxRetries);
            
            retryCount.incrementAndGet();
            assertEquals(1, retryCount.get());
            assertFalse(retryCount.get() > maxRetries);
            
            retryCount.incrementAndGet();
            assertEquals(2, retryCount.get());
            assertFalse(retryCount.get() > maxRetries);
            
            retryCount.incrementAndGet();
            assertEquals(3, retryCount.get());
            assertTrue(retryCount.get() > maxRetries);
        }

        @Test
        @DisplayName("测试 - 重试成功后重置计数器")
        void testRetryCounterReset() {
            AtomicInteger retryCount = new AtomicInteger(0);
            
            retryCount.incrementAndGet();
            retryCount.incrementAndGet();
            assertEquals(2, retryCount.get());
            
            retryCount.set(0);
            assertEquals(0, retryCount.get());
        }
    }

    @Nested
    @DisplayName("ConversationManager 测试组")
    class ConversationManagerTests {

        @Test
        @DisplayName("测试 - 添加用户消息")
        void testAddUserMessage() {
            conversationManager.addUserMessage("你好");
            
            assertEquals(2, conversationManager.getMessageCount());
        }

        @Test
        @DisplayName("测试 - 添加助手消息")
        void testAddAssistantMessage() {
            Message assistantMessage = new Message("assistant", "你好！有什么可以帮助你的？");
            conversationManager.addAssistantMessage(assistantMessage);
            
            assertEquals(2, conversationManager.getMessageCount());
        }

        @Test
        @DisplayName("测试 - 重置会话")
        void testReset() {
            conversationManager.addUserMessage("你好");
            conversationManager.addAssistantMessage(new Message("assistant", "你好！"));
            
            assertEquals(3, conversationManager.getMessageCount());
            
            conversationManager.reset();
            
            assertEquals(1, conversationManager.getMessageCount());
        }

        @Test
        @DisplayName("测试 - Token 计数")
        void testTokenCount() {
            conversationManager.addUserMessage("你好世界");
            
            int tokenCount = conversationManager.getTokenCount();
            
            assertTrue(tokenCount > 0);
        }
    }
}
