package com.example.agent.memory;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BackgroundExtractorTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private TokenEstimator tokenEstimator;

    private BackgroundExtractor extractor;
    private String testSessionId;
    private SessionCompactionState compactionState;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSessionId = "test-session-" + System.currentTimeMillis();
        compactionState = new SessionCompactionState();
        extractor = new BackgroundExtractor(testSessionId, tokenEstimator, llmClient, compactionState);
    }

    @Test
    void testShouldNotExtractBelowInitialThreshold() throws Exception {
        Method shouldExtract = BackgroundExtractor.class.getDeclaredMethod(
            "shouldExtract", List.class);
        shouldExtract.setAccessible(true);

        List<Message> messages = createConversation(3);
        when(tokenEstimator.estimate(messages)).thenReturn(1000);

        boolean result = (boolean) shouldExtract.invoke(extractor, messages);
        assertFalse(result);
    }

    @Test
    void testFirstExtractionDoesNotRequireTokenGrowth() throws Exception {
        Field lastExtracted = BackgroundExtractor.class.getDeclaredField(
            "lastExtractedTokenCount");
        lastExtracted.setAccessible(true);
        lastExtracted.set(extractor, 0);

        Method shouldExtract = BackgroundExtractor.class.getDeclaredMethod(
            "shouldExtract", List.class);
        shouldExtract.setAccessible(true);

        List<Message> messages = createConversation(10);
        when(tokenEstimator.estimate(messages)).thenReturn(10000);

        Field toolCount = BackgroundExtractor.class.getDeclaredField(
            "toolCallCountSinceLastExtraction");
        toolCount.setAccessible(true);
        ((AtomicInteger) toolCount.get(extractor)).set(3);

        boolean result = (boolean) shouldExtract.invoke(extractor, messages);
        assertTrue(result, "第一次达到阈值时应该不需要额外的 Token 增长");
    }

    @Test
    void testSubsequentExtractionRequiresGrowth() throws Exception {
        Field lastExtracted = BackgroundExtractor.class.getDeclaredField(
            "lastExtractedTokenCount");
        lastExtracted.setAccessible(true);
        lastExtracted.set(extractor, 10000);

        Method shouldExtract = BackgroundExtractor.class.getDeclaredMethod(
            "shouldExtract", List.class);
        shouldExtract.setAccessible(true);

        List<Message> messages = createConversation(10);

        when(tokenEstimator.estimate(messages)).thenReturn(12000);
        boolean resultNoGrowth = (boolean) shouldExtract.invoke(extractor, messages);
        assertFalse(resultNoGrowth, "后续提取需要增长 5000 Token");

        when(tokenEstimator.estimate(messages)).thenReturn(15000);
        boolean resultWithGrowth = (boolean) shouldExtract.invoke(extractor, messages);
        assertTrue(resultWithGrowth, "增长 5000 后应该可以提取");
    }

    @Test
    void testMergeMemories() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "mergeMemories", String.class, String.class);
        method.setAccessible(true);

        String existing = "# Session Memory\n\n## 关键决策\n\n- 决策 1";
        String extracted = "## 关键决策\n\n- 决策 2";

        String result = (String) method.invoke(extractor, existing, extracted);

        assertTrue(result.contains("决策 1"));
        assertTrue(result.contains("决策 2"));
        assertTrue(result.contains("---"));
    }

    @Test
    void testFirstExtractionCreatesProperHeader() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "mergeMemories", String.class, String.class);
        method.setAccessible(true);

        String extracted = "## 关键决策\n\n- 决策 1";
        String result = (String) method.invoke(extractor, null, extracted);

        assertTrue(result.contains("# Session Memory"));
        assertTrue(result.contains("决策 1"));
        assertTrue(result.contains("Auto-extracted"));
    }

    @Test
    void testBuildExtractionContextIncludesSystemPrompt() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "buildExtractionContext", List.class);
        method.setAccessible(true);

        List<Message> fullConversation = createConversation(5);
        List<Message> result = (List<Message>) method.invoke(extractor, fullConversation);

        assertTrue(result.size() > 0);
        assertTrue(result.get(0).isSystem());
        assertTrue(result.get(0).getContent().contains("会话记忆提取任务"));
    }

    @Test
    void testBuildExtractionContextPassesExistingMemory() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "buildExtractionContext", List.class);
        method.setAccessible(true);

        extractor.getMemoryManager().write("# Session Memory\n\n- 已有记忆");

        List<Message> fullConversation = createConversation(5);
        List<Message> result = (List<Message>) method.invoke(extractor, fullConversation);

        boolean hasExistingMemory = result.stream()
            .anyMatch(m -> m.getContent() != null && m.getContent().contains("已有记忆"));
        assertTrue(hasExistingMemory, "已有记忆应该传递给 LLM");
    }

    @Test
    void testHasToolCallsInLastAssistantTurn() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "hasToolCallsInLastAssistantTurn", List.class);
        method.setAccessible(true);

        List<Message> messagesWithTools = new ArrayList<>();
        messagesWithTools.add(Message.user("Hello"));
        Message assistantWithTools = Message.assistant("");
        com.example.agent.llm.model.ToolCall toolCall = new com.example.agent.llm.model.ToolCall();
        assistantWithTools.setToolCalls(List.of(toolCall));
        messagesWithTools.add(assistantWithTools);

        boolean result = (boolean) method.invoke(extractor, messagesWithTools);
        assertTrue(result);

        List<Message> messagesWithoutTools = List.of(
            Message.user("Hello"),
            Message.assistant("Hi there!")
        );
        result = (boolean) method.invoke(extractor, messagesWithoutTools);
        assertFalse(result);
    }

    @Test
    void testToolRoleMessageIncrementsCounter() throws Exception {
        Field field = BackgroundExtractor.class.getDeclaredField(
            "toolCallCountSinceLastExtraction");
        field.setAccessible(true);

        List<Message> messages = createConversation(5);

        int before = ((AtomicInteger) field.get(extractor)).get();

        Message toolResultMsg = Message.toolResult("call-1", "bash", "output");
        extractor.onMessageAdded(toolResultMsg, messages);

        int after = ((AtomicInteger) field.get(extractor)).get();
        assertEquals(before + 1, after, "role=tool 的消息应该计数");
    }

    @Test
    void testUserMessageDoesNotIncrementToolCount() throws Exception {
        Field field = BackgroundExtractor.class.getDeclaredField(
            "toolCallCountSinceLastExtraction");
        field.setAccessible(true);

        List<Message> messages = createConversation(5);

        int before = ((AtomicInteger) field.get(extractor)).get();

        Message userMsg = Message.user("Hello");
        extractor.onMessageAdded(userMsg, messages);

        int after = ((AtomicInteger) field.get(extractor)).get();
        assertEquals(before, after, "用户消息不应该增加工具计数");
    }

    @Test
    void testFindBoundaryIndexWithKnownMessageId() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "findBoundaryIndex", List.class);
        method.setAccessible(true);

        Field field = BackgroundExtractor.class.getDeclaredField("lastExtractedMessageId");
        field.setAccessible(true);
        field.set(extractor, "msg-5");

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Message msg = Message.user("msg-" + i);
            msg.setId("msg-" + i);
            messages.add(msg);
        }

        int index = (int) method.invoke(extractor, messages);
        assertEquals(0, index, "找到 msg-5 后，往前回退 5 条作为起点");
    }

    @Test
    void testFindBoundaryIndexFallsBack() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "findBoundaryIndex", List.class);
        method.setAccessible(true);

        Field field = BackgroundExtractor.class.getDeclaredField("lastExtractedMessageId");
        field.setAccessible(true);
        field.set(extractor, "non-existent-id");

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            Message msg = Message.user("msg-" + i);
            msg.setId("msg-" + i);
            messages.add(msg);
        }

        int index = (int) method.invoke(extractor, messages);
        assertEquals(50, index, "找不到时回退到最后 100 条");
    }

    @Test
    void testRemoveSessionMemoryHeader() throws Exception {
        Method method = BackgroundExtractor.class.getDeclaredMethod(
            "removeSessionMemoryHeader", String.class);
        method.setAccessible(true);

        String input = "# Session Memory\n\n## 关键决策\n\n- 决策 1\n\n---\n> Auto-extracted at 123456";
        String result = (String) method.invoke(extractor, input);

        assertFalse(result.contains("# Session Memory"));
        assertFalse(result.contains("Auto-extracted at"));
        assertTrue(result.contains("关键决策"));
        assertTrue(result.contains("决策 1"));
    }

    @Test
    void testExtractRunsInBackground() throws Exception {
        List<Message> messages = createConversation(20);
        when(tokenEstimator.estimate(messages)).thenReturn(15000);

        CountDownLatch latch = new CountDownLatch(1);
        when(llmClient.generateSync(anyString())).thenAnswer(invocation -> {
            latch.countDown();
            return "## 关键决策\n\n- 决策内容";
        });

        Field field = BackgroundExtractor.class.getDeclaredField(
            "toolCallCountSinceLastExtraction");
        field.setAccessible(true);
        ((AtomicInteger) field.get(extractor)).set(5);

        extractor.checkAndExtract(messages);

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "后台提取应该在 5 秒内完成");

        verify(llmClient, times(1)).generateSync(anyString());
    }

    @Test
    void testExtractGracefullyHandlesLlmErrors() throws Exception {
        List<Message> messages = createConversation(20);
        when(tokenEstimator.estimate(messages)).thenReturn(15000);

        CountDownLatch latch = new CountDownLatch(1);
        when(llmClient.generateSync(anyString())).thenAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("LLM API Error");
        });

        Field field = BackgroundExtractor.class.getDeclaredField(
            "toolCallCountSinceLastExtraction");
        field.setAccessible(true);
        ((AtomicInteger) field.get(extractor)).set(5);

        Field inProgress = BackgroundExtractor.class.getDeclaredField(
            "extractionInProgress");
        inProgress.setAccessible(true);
        ((AtomicBoolean) inProgress.get(extractor)).set(false);

        extractor.checkAndExtract(messages);

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "即使 LLM 报错也应该完成执行");

        Thread.sleep(100);

        boolean stillRunning = ((AtomicBoolean) inProgress.get(extractor)).get();
        assertFalse(stillRunning, "出错后应该释放锁，不能卡住");
    }

    @Test
    void testConcurrentExtractionPreventsParallelExecution() throws Exception {
        List<Message> messages = createConversation(20);
        when(tokenEstimator.estimate(messages)).thenReturn(15000);

        CountDownLatch extractStart = new CountDownLatch(1);
        CountDownLatch extractHold = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        when(llmClient.generateSync(anyString())).thenAnswer(invocation -> {
            executionCount.incrementAndGet();
            extractStart.countDown();
            extractHold.await(3, TimeUnit.SECONDS);
            return "记忆内容";
        });

        Field field = BackgroundExtractor.class.getDeclaredField(
            "toolCallCountSinceLastExtraction");
        field.setAccessible(true);
        ((AtomicInteger) field.get(extractor)).set(10);

        Field executorField = BackgroundExtractor.class.getDeclaredField("EXECUTOR");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(null);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> extractor.checkAndExtract(messages));
        }

        extractStart.await(5, TimeUnit.SECONDS);
        extractHold.countDown();

        Thread.sleep(500);

        assertEquals(1, executionCount.get(), "并发调用也应该只执行一次提取");
    }

    private List<Message> createConversation(int count) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("You are a helpful assistant."));
        for (int i = 0; i < count; i++) {
            messages.add(Message.user("User message " + i));
            messages.add(Message.assistant("Assistant response " + i));
        }
        return messages;
    }
}
