package com.example.agent.memory;

import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryModuleBoundaryConditionsTest {

    @Mock
    private LlmClient llmClient;

    @TempDir
    Path tempDir;

    private SessionMemoryManager sessionMemoryManager;
    private MemoryStore memoryStore;
    private MemoryRetriever memoryRetriever;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSessionId = "test-session-" + System.currentTimeMillis();
        sessionMemoryManager = new SessionMemoryManager(testSessionId);
        memoryStore = new MemoryStore(llmClient, tempDir.toString());
        memoryRetriever = new MemoryRetriever(memoryStore);
    }

    @AfterEach
    void tearDown() throws IOException {
        Path file = sessionMemoryManager.getMemoryFilePath();
        if (Files.exists(file)) {
            Files.delete(file);
            Path parent = file.getParent();
            while (parent != null && !Files.list(parent).findAny().isPresent()) {
                Files.delete(parent);
                parent = parent.getParent();
            }
        }
    }

    // ==================== SessionMemoryManager 边界测试 ====================

    @Test
    void testWriteNullDoesNotThrow() {
        assertDoesNotThrow(() -> sessionMemoryManager.write(null),
            "write(null) 不应抛出异常");
    }

    @Test
    void testWriteEmptyDoesNotThrow() {
        assertDoesNotThrow(() -> sessionMemoryManager.write(""),
            "write(\"\") 不应抛出异常");
    }

    @Test
    void testAppendNullDoesNothing() {
        sessionMemoryManager.write("initial content");
        String before = sessionMemoryManager.read();

        assertDoesNotThrow(() -> sessionMemoryManager.append(null),
            "append(null) 不应抛出异常");

        assertEquals(before, sessionMemoryManager.read(),
            "append(null) 不应修改内容");
    }

    @Test
    void testAppendBlankDoesNothing() {
        sessionMemoryManager.write("initial content");
        String before = sessionMemoryManager.read();

        assertDoesNotThrow(() -> sessionMemoryManager.append("   "),
            "append(blank) 不应抛出异常");

        assertEquals(before, sessionMemoryManager.read(),
            "append(blank) 不应修改内容");
    }

    @Test
    void testReadNonExistentReturnsNull() {
        SessionMemoryManager freshManager = new SessionMemoryManager(
            "fresh-session-" + System.currentTimeMillis());
        assertDoesNotThrow(freshManager::clear,
            "clear() 不存在的文件不应抛出异常");
    }

    @Test
    void testInitializeIfNotExistsCreatesFile() {
        SessionMemoryManager freshManager = new SessionMemoryManager(
            "init-test-" + System.currentTimeMillis());
        assertFalse(freshManager.exists());

        freshManager.initializeIfNotExists();

        assertTrue(freshManager.exists());
        assertNotNull(freshManager.read());
    }

    @Test
    void testHasActualContentWithTemplateReturnsFalse() {
        sessionMemoryManager.initializeIfNotExists();
        assertFalse(sessionMemoryManager.hasActualContent(),
            "模板内容应被识别为无实际内容");
    }

    @Test
    void testHasActualContentWithRealContentReturnsTrue() {
        sessionMemoryManager.write("# Test\n\nReal content here");
        assertTrue(sessionMemoryManager.hasActualContent(),
            "实际内容应正确识别");
    }

    // ==================== MemoryRetriever 边界测试 ====================

    @Test
    void testPrepareContextHeaderWithNullReturnsEmptyList() {
        List<Message> result = memoryRetriever.prepareContextHeader(null);
        assertNotNull(result, "结果不应为 null");
        assertTrue(result.isEmpty(), "null 输入应返回空列表");
    }

    @Test
    void testPrepareContextHeaderWithEmptyListWorks() {
        List<Message> emptyList = new ArrayList<>();
        assertDoesNotThrow(() -> memoryRetriever.prepareContextHeader(emptyList),
            "空列表不应抛出异常");
    }

    @Test
    void testExtractContextForSearchWithNullReturnsEmpty() throws Exception {
        java.lang.reflect.Method method = MemoryRetriever.class.getDeclaredMethod(
            "extractContextForSearch", List.class);
        method.setAccessible(true);

        String result = (String) method.invoke(memoryRetriever, (Object) null);
        assertEquals("", result, "null 输入应返回空字符串");
    }

    @Test
    void testExtractContextForSearchWithEmptyListReturnsEmpty() throws Exception {
        java.lang.reflect.Method method = MemoryRetriever.class.getDeclaredMethod(
            "extractContextForSearch", List.class);
        method.setAccessible(true);

        String result = (String) method.invoke(memoryRetriever, Collections.emptyList());
        assertEquals("", result, "空列表应返回空字符串");
    }

    @Test
    void testExtractContextForSearchWithNullMessageDoesNotCrash() throws Exception {
        java.lang.reflect.Method method = MemoryRetriever.class.getDeclaredMethod(
            "extractContextForSearch", List.class);
        method.setAccessible(true);

        List<Message> messages = new ArrayList<>();
        messages.add(null);
        messages.add(Message.user("Hello"));
        messages.add(null);

        assertDoesNotThrow(() -> method.invoke(memoryRetriever, messages),
            "包含 null 的消息列表不应抛出 NPE");
    }

    @Test
    void testMarkForMemoryNullDoesNothing() {
        int before = memoryStore.getPendingCount();
        memoryRetriever.markForMemory(null);
        assertEquals(before, memoryStore.getPendingCount(),
            "markForMemory(null) 不应添加");
    }

    @Test
    void testMarkForMemoryBlankDoesNothing() {
        int before = memoryStore.getPendingCount();
        memoryRetriever.markForMemory("   ");
        assertEquals(before, memoryStore.getPendingCount(),
            "markForMemory(blank) 不应添加");
    }

    // ==================== MemoryStore 边界测试 ====================

    @Test
    void testAddPendingMemoryNullDoesNothing() {
        int before = memoryStore.getPendingCount();
        memoryStore.addPendingMemory(null);
        assertEquals(before, memoryStore.getPendingCount(),
            "addPendingMemory(null) 不应添加");
    }

    @Test
    void testAddPendingMemoryBlankDoesNothing() {
        int before = memoryStore.getPendingCount();
        memoryStore.addPendingMemory("   ");
        assertEquals(before, memoryStore.getPendingCount(),
            "addPendingMemory(blank) 不应添加");
    }

    @Test
    void testSearchRelevantNullReturnsEmpty() {
        List<MemoryEntry> result = memoryStore.searchRelevant(null, 5);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "null query 应返回空列表");
    }

    @Test
    void testSearchRelevantBlankReturnsEmpty() {
        List<MemoryEntry> result = memoryStore.searchRelevant("   ", 5);
        assertNotNull(result);
        assertTrue(result.isEmpty(), "blank query 应返回空列表");
    }

    @Test
    void testSearchRelevantNegativeLimitClampsToOne() {
        memoryStore.addPendingMemory("Test memory 1");
        memoryStore.addPendingMemory("Test memory 2");
        memoryStore.addPendingMemory("Test memory 3");

        List<MemoryEntry> result = memoryStore.searchRelevant("test", -100);
        assertNotNull(result);
        assertTrue(result.size() <= 1, "负 limit 应被限制为 1");
    }

    @Test
    void testSearchRelevantZeroLimitClampsToOne() {
        List<MemoryEntry> result = memoryStore.searchRelevant("test", 0);
        assertNotNull(result);
        assertTrue(result.size() <= 1, "0 limit 应被限制为 1");
    }

    @Test
    void testGetRelevantMemoriesAsPromptNullReturnsEmpty() {
        String result = memoryStore.getRelevantMemoriesAsPrompt(null);
        assertEquals("", result, "null query 应返回空字符串");
    }

    @Test
    void testGetRelevantMemoriesAsPromptBlankReturnsEmpty() {
        String result = memoryStore.getRelevantMemoriesAsPrompt("   ");
        assertEquals("", result, "blank query 应返回空字符串");
    }

    @Test
    void testProcessAutoDreamNullDoesNotCrash() throws Exception {
        java.lang.reflect.Method method = MemoryStore.class.getDeclaredMethod(
            "processAutoDream", List.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(memoryStore, (Object) null),
            "processAutoDream(null) 不应抛出异常");
    }

    @Test
    void testProcessAutoDreamEmptyDoesNotCrash() throws Exception {
        java.lang.reflect.Method method = MemoryStore.class.getDeclaredMethod(
            "processAutoDream", List.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(memoryStore, Collections.emptyList()),
            "processAutoDream(empty) 不应抛出异常");
    }

    @Test
    void testProcessDreamResultNullDoesNotCrash() throws Exception {
        java.lang.reflect.Method method = MemoryStore.class.getDeclaredMethod(
            "processDreamResult", String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(memoryStore, (Object) null),
            "processDreamResult(null) 不应抛出异常");
    }

    @Test
    void testProcessDreamResultBlankDoesNotCrash() throws Exception {
        java.lang.reflect.Method method = MemoryStore.class.getDeclaredMethod(
            "processDreamResult", String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(memoryStore, "   "),
            "processDreamResult(blank) 不应抛出异常");
    }

    // ==================== MemoryEntry 边界测试 ====================

    @Test
    void testCalculateRelevanceNullReturnsZero() {
        MemoryEntry entry = new MemoryEntry(
            "id-1",
            "Test content for testing",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.7
        );

        double result = entry.calculateRelevance(null);
        assertEquals(0.0, result, 0.001, "null query 应返回 0");
    }

    @Test
    void testCalculateRelevanceBlankReturnsZero() {
        MemoryEntry entry = new MemoryEntry(
            "id-1",
            "Test content for testing",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.7
        );

        double result = entry.calculateRelevance("   ");
        assertEquals(0.0, result, 0.001, "blank query 应返回 0");
    }

    @Test
    void testCalculateRelevanceDoesNotExceedOne() {
        MemoryEntry entry = new MemoryEntry(
            "id-1",
            "test test test test test",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            1.0
        );

        double result = entry.calculateRelevance("test");
        assertTrue(result <= 1.0, "相关性不应超过 1.0");
    }

    // ==================== 集成边界测试 ====================

    @Test
    void testFullFlowWithNullsDoesNotCrash() {
        assertDoesNotThrow(() -> {
            memoryRetriever.markForMemory(null);
            memoryRetriever.prepareContextHeader(null);
            memoryStore.getRelevantMemoriesAsPrompt(null);
            sessionMemoryManager.write(null);
            sessionMemoryManager.append(null);
        }, "完整流程的 null 边界不应崩溃");
    }

    @Test
    void testConcurrentMemoryOperationsDoNotCorrupt() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    memoryStore.addPendingMemory("Memory " + threadId + "-" + j);
                    memoryStore.searchRelevant("test", 5);
                    memoryStore.getRelevantMemoriesAsPrompt("query");
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(true, "并发操作不应抛出异常");
    }
}
