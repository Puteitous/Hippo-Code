package com.example.agent.memory.extraction;

import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionTriggerTest {

    private ExtractionTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new ExtractionTrigger(2); // 每 2 轮触发一次
    }

    @Test
    void testShouldExtractWhenIntervalReached() {
        List<Message> conversation = createConversation(4);

        // 第一次：未达到间隔
        assertFalse(trigger.shouldExtract(conversation));
        
        // 第二次：达到间隔
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    void testShouldNotExtractWhenMemoryWritten() {
        List<Message> conversation = createConversation(4);
        
        // 模拟主 Agent 已写记忆
        trigger.notifyMemoryWritten("msg-1");
        
        // 即使达到间隔也不应提取
        assertFalse(trigger.shouldExtract(conversation));
    }

    @Test
    void testResetTurnsCounter() {
        List<Message> conversation = createConversation(4);
        
        // 触发一次
        trigger.shouldExtract(conversation);
        
        // 重置计数器
        trigger.resetTurnsCounter();
        
        // 应该重新开始计数
        assertFalse(trigger.shouldExtract(conversation));
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    void testGetTurnsSinceLastExtraction() {
        List<Message> conversation = createConversation(2);
        
        assertEquals(0, trigger.getTurnsSinceLastExtraction());
        
        trigger.shouldExtract(conversation);
        assertEquals(1, trigger.getTurnsSinceLastExtraction());
        
        trigger.shouldExtract(conversation);
        assertEquals(0, trigger.getTurnsSinceLastExtraction()); // 触发后重置
    }

    @Test
    void testSetExtractionInterval() {
        trigger.setExtractionInterval(3);
        
        List<Message> conversation = createConversation(6);
        
        assertFalse(trigger.shouldExtract(conversation));
        assertFalse(trigger.shouldExtract(conversation));
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    void testDefaultConstructor() {
        ExtractionTrigger defaultTrigger = new ExtractionTrigger();
        List<Message> conversation = createConversation(2);
        
        // 默认间隔为 1
        assertTrue(defaultTrigger.shouldExtract(conversation));
    }

    private List<Message> createConversation(int messageCount) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            messages.add(Message.user("User message " + i));
            messages.add(Message.assistant("Assistant response " + i));
        }
        return messages;
    }
}
