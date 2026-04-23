package com.example.agent;

import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Choice;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("空响应重试机制测试")
class EmptyResponseRetryTest {

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
        
        assertFalse(response.hasContent());
        assertEquals("", response.getContent());
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
    @DisplayName("测试 - hasContent 方法：null choices")
    void testHasContentWithNullChoices() {
        ChatResponse response = new ChatResponse();
        response.setChoices(null);
        
        assertFalse(response.hasContent());
        assertEquals("", response.getContent());
    }

    @Test
    @DisplayName("测试 - getFirstMessage 方法：获取第一条消息")
    void testGetFirstMessage() {
        ChatResponse response = new ChatResponse();
        Message msg1 = new Message("assistant", "First");
        Message msg2 = new Message("assistant", "Second");
        Choice choice1 = new Choice();
        choice1.setMessage(msg1);
        Choice choice2 = new Choice();
        choice2.setMessage(msg2);
        response.setChoices(List.of(choice1, choice2));
        
        assertEquals(msg1, response.getFirstMessage());
    }

    @Test
    @DisplayName("测试 - hasToolCalls 方法：检测工具调用")
    void testHasToolCalls() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(Message.assistantWithToolCalls(new ArrayList<>()));
        response.setChoices(List.of(choice));
        
        assertNotNull(choice.getMessage().getToolCalls());
    }

    @Test
    @DisplayName("测试 - Usage 统计正常工作")
    void testUsageMetrics() {
        Usage usage = new Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(150);
        
        ChatResponse response = new ChatResponse();
        response.setUsage(usage);
        
        assertEquals(100, response.getUsage().getPromptTokens());
        assertEquals(50, response.getUsage().getCompletionTokens());
        assertEquals(150, response.getUsage().getTotalTokens());
    }

    @Test
    @DisplayName("测试 - 空内容识别")
    void testEmptyContentDetection() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(new Message("assistant", ""));
        response.setChoices(List.of(choice));
        
        assertFalse(response.hasContent());
    }

    @Test
    @DisplayName("测试 - 有内容识别")
    void testNonEmptyContentDetection() {
        ChatResponse response = new ChatResponse();
        Choice choice = new Choice();
        choice.setMessage(new Message("assistant", "Hello there!"));
        response.setChoices(List.of(choice));
        
        assertTrue(response.hasContent());
    }
}
