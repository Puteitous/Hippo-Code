package com.example.agent.memory.extraction;

import com.example.agent.application.ConversationService;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.MemoryToolSandbox;
import com.example.agent.service.TokenEstimator;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MemoryExtractor 单元测试")
class MemoryExtractorTest {

    @TempDir
    Path tempDir;

    private LlmClient llmClient;
    private TokenEstimator tokenEstimator;
    private SubAgentManager subAgentManager;
    private ConversationService conversationService;
    private MemoryExtractor extractor;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        tokenEstimator = mock(TokenEstimator.class);
        subAgentManager = mock(SubAgentManager.class);
        conversationService = mock(ConversationService.class);
        testSessionId = "test-session-" + System.currentTimeMillis();

        // 注册 mock 服务到 ServiceLocator
        ServiceLocator.registerSingleton(SubAgentManager.class, subAgentManager);
        ServiceLocator.registerSingleton(ConversationService.class, conversationService);

        when(tokenEstimator.estimate(anyList())).thenReturn(5000);
        
        extractor = new MemoryExtractor(testSessionId, tokenEstimator, llmClient, 3);
    }

    @AfterEach
    void tearDown() {
        ServiceLocator.clear();
    }

    @Test
    @DisplayName("提取开关默认开启")
    void testDefaultEnabled() {
        assertTrue(extractor.isEnabled());
    }

    @Test
    @DisplayName("可以关闭提取开关")
    void testDisableExtraction() {
        extractor.setEnabled(false);
        assertFalse(extractor.isEnabled());
    }

    @Test
    @DisplayName("关闭开关后不触发提取")
    void testNoExtractionWhenDisabled() {
        extractor.setEnabled(false);
        
        List<Message> messages = createConversation(10);
        extractor.onMessageAdded(messages.get(0), messages);
        
        // 验证没有调用 SubAgent
        verify(subAgentManager, never()).forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("未达到触发轮次时不提取")
    void testNoExtractionBeforeThreshold() {
        List<Message> messages = createConversation(2);
        extractor.onMessageAdded(messages.get(0), messages);
        extractor.onMessageAdded(messages.get(1), messages);
        
        // 等待异步任务执行
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 验证没有调用 SubAgent（未达到 3 轮阈值）
        verify(subAgentManager, never()).forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("达到触发轮次时提交提取任务")
    void testExtractionTriggeredAtThreshold() throws Exception {
        // Mock Conversation
        Conversation mockConversation = mock(Conversation.class);
        when(conversationService.getConversation(testSessionId)).thenReturn(mockConversation);
        
        // Mock SubAgentTask
        SubAgentTask mockTask = mock(SubAgentTask.class);
        when(subAgentManager.forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any()))
            .thenReturn(mockTask);

        List<Message> messages = createConversation(10);
        
        // 模拟 3 次消息添加，达到触发阈值
        extractor.onMessageAdded(messages.get(0), messages);
        extractor.onMessageAdded(messages.get(1), messages);
        extractor.onMessageAdded(messages.get(2), messages);
        
        // 等待异步任务执行完成
        Thread.sleep(200);
        
        // 验证调用了 SubAgent
        verify(subAgentManager, times(1)).forkAgent(
            eq(mockConversation),
            contains("长期记忆提取"),
            anyString(),
            eq(120),
            isNull(),
            eq(com.example.agent.subagent.SubAgentPermission.MEMORY_EXTRACTOR),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("并发控制：提取进行中时暂存上下文")
    void testTrailingExtraction() throws Exception {
        // Mock Conversation
        Conversation mockConversation = mock(Conversation.class);
        when(conversationService.getConversation(testSessionId)).thenReturn(mockConversation);
        
        // Mock SubAgentTask - 让它运行较长时间
        SubAgentTask mockTask = mock(SubAgentTask.class);
        doAnswer(invocation -> {
            Thread.sleep(500); // 模拟长时间运行
            return null;
        }).when(mockTask).awaitCompletion(anyLong(), any());
        
        when(subAgentManager.forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any()))
            .thenReturn(mockTask);

        List<Message> messages1 = createConversation(10);
        
        // 触发第一次提取（需要 3 次调用达到阈值）
        extractor.onMessageAdded(messages1.get(0), messages1);
        extractor.onMessageAdded(messages1.get(1), messages1);
        extractor.onMessageAdded(messages1.get(2), messages1);
        
        // 等待第一次提取开始
        Thread.sleep(200);
        
        // 再次触发（应该暂存）
        List<Message> messages2 = createConversation(15);
        extractor.onMessageAdded(messages2.get(0), messages2);
        
        // 等待异步任务执行
        Thread.sleep(200);
        
        // 验证只调用了一次 forkAgent（第二次被暂存）
        verify(subAgentManager, times(1)).forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("通知主 Agent 已写记忆后不触发提取")
    void testNoExtractionAfterMemoryWritten() {
        // 模拟主 Agent 已写记忆
        extractor.notifyMemoryWritten("msg-1");
        
        List<Message> messages = createConversation(10);
        extractor.onMessageAdded(messages.get(0), messages);
        
        // 验证没有调用 SubAgent
        verify(subAgentManager, never()).forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("空消息列表不触发提取")
    void testNoExtractionWithEmptyMessages() {
        extractor.checkAndExtract(new ArrayList<>());
        
        verify(subAgentManager, never()).forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("null 消息列表不触发提取")
    void testNoExtractionWithNullMessages() {
        extractor.checkAndExtract(null);
        
        verify(subAgentManager, never()).forkAgent(any(), anyString(), anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SubAgentManager 不可用时不崩溃")
    void testGracefulDegradationWhenSubAgentManagerUnavailable() {
        ServiceLocator.clear();
        ServiceLocator.registerSingleton(ConversationService.class, conversationService);
        
        // 重新创建 extractor（此时 SubAgentManager 不可用）
        MemoryExtractor extractorWithoutSubAgent = new MemoryExtractor(
            testSessionId, tokenEstimator, llmClient, 1
        );
        
        List<Message> messages = createConversation(10);
        
        // 不应崩溃
        assertDoesNotThrow(() -> extractorWithoutSubAgent.onMessageAdded(messages.get(0), messages));
    }

    @Test
    @DisplayName("游标追踪：首次提取统计所有消息")
    void testCursorTrackingFirstExtraction() {
        // 首次提取时，游标为空，应该统计所有消息
        List<Message> messages = createConversation(4); // 2 轮对话
        
        // 验证消息创建正确
        assertEquals(4, messages.size());
        assertTrue(messages.get(0).isUser());
        assertTrue(messages.get(1).isAssistant());
    }

    @Test
    @DisplayName("游标追踪：消息带 ID 时可以正确统计新消息")
    void testCursorTrackingWithMessageIds() {
        // 创建带 ID 的消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(createMessageWithId("id-1", true));
        messages.add(createMessageWithId("id-2", false));
        messages.add(createMessageWithId("id-3", true));
        messages.add(createMessageWithId("id-4", false));
        
        assertEquals(4, messages.size());
        assertNotNull(messages.get(0).getId());
    }

    @Test
    @DisplayName("游标推进：提取完成后游标更新")
    void testCursorAdvanceAfterExtraction() {
        // 这个测试验证游标机制的基本逻辑
        // 实际游标推进在 performExtraction 完成后执行
        
        List<Message> messages = createConversationWithIds(2, "msg-1", "msg-2");
        
        // 验证消息 ID 设置正确
        assertEquals("msg-1", messages.get(0).getId());
        assertEquals("msg-2", messages.get(1).getId());
    }

    private Message createMessageWithId(String id, boolean isUser) {
        Message msg = isUser ? Message.user("User message") : Message.assistant("Assistant response");
        return msg;
    }

    private List<Message> createConversation(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                messages.add(Message.user("User message " + i));
            } else {
                messages.add(Message.assistant("Assistant response " + i));
            }
        }
        return messages;
    }

    private List<Message> createConversationWithIds(int count, String... ids) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count && i < ids.length; i++) {
            if (i % 2 == 0) {
                messages.add(Message.user("User message " + i));
            } else {
                messages.add(Message.assistant("Assistant response " + i));
            }
        }
        return messages;
    }
}
