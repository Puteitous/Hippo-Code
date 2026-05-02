package com.example.agent.tools;

import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.embedding.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchMemoryToolTest {

    private MemoryStore memoryStore;
    private EmbeddingService embeddingService;
    private SearchMemoryTool searchMemoryTool;
    private ObjectMapper objectMapper;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        memoryStore = mock(MemoryStore.class);
        embeddingService = mock(EmbeddingService.class);
        searchMemoryTool = new SearchMemoryTool(memoryStore, embeddingService);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("工具名称正确")
    void testToolName() {
        assertEquals("search_memory", searchMemoryTool.getName());
    }

    @Test
    @DisplayName("空查询参数抛出异常")
    void testEmptyQuery() {
        JsonNode arguments = objectMapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).put("query", "");

        assertThrows(ToolExecutionException.class, () -> {
            searchMemoryTool.execute(arguments);
        });
    }

    @Test
    @DisplayName("EmbeddingService不可用时返回错误信息")
    void testEmbeddingServiceNotAvailable() throws ToolExecutionException {
        when(embeddingService.isAvailable()).thenReturn(false);

        JsonNode arguments = objectMapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).put("query", "test query");

        String result = searchMemoryTool.execute(arguments);

        assertTrue(result.contains("Vector search is not available"));
    }

    @Test
    @DisplayName("正常检索返回结果")
    void testSuccessfulSearch() throws Exception {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(embedding);

        MemoryEntry entry = createMemoryEntry("test-id", "Test Title", "# Test Title\nThis is test content");
        when(memoryStore.searchSimilar(eq(embedding), eq(5), eq(0.6)))
            .thenReturn(List.of(entry));

        JsonNode arguments = objectMapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).put("query", "test query");

        String result = searchMemoryTool.execute(arguments);

        assertTrue(result.contains("Found 1 relevant memories"));
        assertTrue(result.contains("Test Title"));
        assertTrue(result.contains("test-id"));
    }

    @Test
    @DisplayName("无结果时返回提示信息")
    void testNoResults() throws Exception {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(embedding);
        when(memoryStore.searchSimilar(eq(embedding), eq(5), eq(0.6)))
            .thenReturn(new ArrayList<>());

        JsonNode arguments = objectMapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).put("query", "test query");

        String result = searchMemoryTool.execute(arguments);

        assertTrue(result.contains("未找到"));
    }

    @Test
    @DisplayName("工具执行响应线程中断")
    void testToolRespondsToInterrupt() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed("test query")).thenAnswer(invocation -> {
            startLatch.countDown();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.set(true);
                throw new RuntimeException("Interrupted during embedding");
            }
            return new float[]{0.1f, 0.2f};
        });

        JsonNode arguments = objectMapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).put("query", "test query");

        Thread toolThread = new Thread(() -> {
            try {
                searchMemoryTool.execute(arguments);
            } catch (ToolExecutionException e) {
                assertTrue(e.getMessage().contains("中断"));
            }
        });
        toolThread.start();

        assertTrue(startLatch.await(2, TimeUnit.SECONDS), "工具应该开始执行");

        toolThread.interrupt();

        toolThread.join(2000);
        assertTrue(interrupted.get(), "工具应该响应中断");
    }

    private MemoryEntry createMemoryEntry(String id, String title, String content) {
        MemoryEntry entry = mock(MemoryEntry.class);
        when(entry.getId()).thenReturn(id);
        when(entry.getContent()).thenReturn(content);
        when(entry.getType()).thenReturn(MemoryEntry.MemoryType.DECISION);
        return entry;
    }
}
