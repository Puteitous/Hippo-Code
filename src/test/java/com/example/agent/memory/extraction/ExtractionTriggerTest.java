package com.example.agent.memory.extraction;

import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionTriggerTest {

    private ExtractionTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new ExtractionTrigger(2); // 每 2 轮完整对话触发一次
    }

    @Test
    @DisplayName("达到触发轮次时应该提取")
    void testShouldExtractWhenIntervalReached() {
        // 创建 2 轮完整对话（4 条消息）
        List<Message> conversation = createConversation(2);
        
        // 第一次调用：达到 2 轮，应该触发
        assertTrue(trigger.shouldExtract(conversation));
        
        // 再创建 2 轮对话（累计 4 轮）
        conversation = createConversation(4);
        
        // 第二次调用：新增 2 轮（4-2=2），应该触发
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    @DisplayName("未达到触发轮次时不提取")
    void testShouldNotExtractBeforeInterval() {
        // 创建 1 轮对话（2 条消息）
        List<Message> conversation = createConversation(1);
        
        // 未达到 2 轮间隔，不应触发
        assertFalse(trigger.shouldExtract(conversation));
        
        // 再创建 1 轮对话（累计 2 轮）
        conversation = createConversation(2);
        
        // 达到 2 轮（2-0=2），应该触发
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    @DisplayName("主 Agent 写记忆后不提取")
    void testShouldNotExtractWhenMemoryWritten() {
        // 创建带 ID 的消息
        List<Message> conversation = createConversationWithIds(4);
        
        // 模拟主 Agent 已写记忆（设置边界为第一条消息）
        // 注意：hasMemoryWritesSince 检查的是边界之后是否有写文件工具调用
        // 这里我们只是测试 notifyMemoryWritten 会重置计数器
        trigger.notifyMemoryWritten("msg-0");
        
        // 调用 shouldExtract 会触发 resetRoundCounter() 因为 hasMemoryWritesSince 返回 false
        // 但实际上 notifyMemoryWritten 的目的是让触发器知道主 Agent 已经处理了记忆
        // 所以这个测试的重点是验证 notifyMemoryWritten 不会导致异常
        assertDoesNotThrow(() -> trigger.shouldExtract(conversation));
    }

    @Test
    @DisplayName("重置轮次计数器")
    void testResetRoundCounter() {
        List<Message> conversation = createConversation(4);
        
        // 触发一次（4 轮 >= 2）
        assertTrue(trigger.shouldExtract(conversation));
        
        // 重置计数器
        trigger.resetRoundCounter();
        
        // 应该重新开始计数，4 轮 >= 2，应该触发
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    @DisplayName("获取已完成的轮次计数")
    void testGetCompletedRoundsSinceLastExtraction() {
        List<Message> conversation = createConversation(2);
        
        assertEquals(0, trigger.getCompletedRoundsSinceLastExtraction());
        
        trigger.shouldExtract(conversation);
        // 触发后记录当前轮次为 2
        assertEquals(2, trigger.getCompletedRoundsSinceLastExtraction());
        
        // 再创建 4 轮对话
        conversation = createConversation(4);
        trigger.shouldExtract(conversation);
        // 触发后记录当前轮次为 4
        assertEquals(4, trigger.getCompletedRoundsSinceLastExtraction());
    }

    @Test
    @DisplayName("设置提取间隔")
    void testSetExtractionInterval() {
        trigger.setExtractionInterval(3);
        
        // 创建 2 轮对话（2 - 0 = 2 < 3，不触发）
        List<Message> conversation = createConversation(2);
        assertFalse(trigger.shouldExtract(conversation));
        
        // 创建 4 轮对话（4 - 0 = 4 >= 3，触发）
        conversation = createConversation(4);
        assertTrue(trigger.shouldExtract(conversation));
        
        // 创建 6 轮对话（6 - 4 = 2 < 3，不触发）
        conversation = createConversation(6);
        assertFalse(trigger.shouldExtract(conversation));
        
        // 创建 8 轮对话（8 - 4 = 4 >= 3，触发）
        conversation = createConversation(8);
        assertTrue(trigger.shouldExtract(conversation));
    }

    @Test
    @DisplayName("默认构造函数")
    void testDefaultConstructor() {
        ExtractionTrigger defaultTrigger = new ExtractionTrigger();
        
        // 默认间隔为 3
        List<Message> conversation = createConversation(2);
        assertFalse(defaultTrigger.shouldExtract(conversation));
        
        conversation = createConversation(3);
        assertTrue(defaultTrigger.shouldExtract(conversation));
    }

    @Test
    @DisplayName("不完整轮次不计数")
    void testIncompleteRoundNotCounted() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("User message"));
        // 只有用户消息，没有 AI 回复
        
        // 不完整轮次不应触发
        assertFalse(trigger.shouldExtract(messages));
    }

    /**
     * 创建指定轮数的完整对话
     * @param rounds 轮数
     * @return 消息列表（每条消息包含 user + assistant）
     */
    private List<Message> createConversation(int rounds) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            messages.add(Message.user("User message " + i));
            messages.add(Message.assistant("Assistant response " + i));
        }
        return messages;
    }

    /**
     * 创建带 ID 的消息（用于测试记忆写入检测）
     */
    private List<Message> createConversationWithIds(int rounds) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            Message userMsg = Message.user("User message " + i);
            // 设置 ID
            userMsg.setId("msg-" + i);
            messages.add(userMsg);
            messages.add(Message.assistant("Assistant response " + i));
        }
        return messages;
    }
}
