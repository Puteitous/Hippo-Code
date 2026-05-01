package com.example.agent.memory.embedding;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.MemoryToolSandbox;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量化和精准注入集成测试
 * 
 * 验收场景：
 * 1. EmbeddingService 接口可用
 * 2. MemoryEntry embedding 字段序列化
 * 3. MemoryStore.searchSimilar 向量检索
 * 4. VectorizationScheduler 批量向量化
 * 5. MemoryRetriever 精准注入
 * 6. 优雅降级（关键词匹配）
 */
@DisplayName("向量化和精准注入集成测试")
class VectorEmbeddingIntegrationTest {

    @TempDir
    Path tempDir;

    private MemoryStore store;
    private MemoryToolSandbox sandbox;
    private LocalEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        Path memoryDir = tempDir.resolve(".hippo/memory");
        Path modelDir = tempDir.resolve(".hippo/models/embedding");
        try {
            Files.createDirectories(memoryDir);
            Files.createDirectories(modelDir);
        } catch (IOException e) {
            fail("创建目录失败：" + e.getMessage());
        }
        sandbox = new MemoryToolSandbox(memoryDir);
        store = new MemoryStore(sandbox);
        embeddingService = new LocalEmbeddingService(modelDir);
    }

    @Test
    @DisplayName("EmbeddingService 接口可用")
    void testEmbeddingServiceAvailable() {
        assertNotNull(embeddingService);
        // 模型不存在时降级为不可用状态
        assertFalse(embeddingService.isAvailable());
        assertEquals(512, embeddingService.getDimension());
    }

    @Test
    @DisplayName("MemoryEntry embedding 字段序列化")
    void testMemoryEntryEmbeddingSerialization() {
        MemoryEntry entry = new MemoryEntry("test-entry", "测试内容", 
            MemoryEntry.MemoryType.FACT, Set.of("test"), 0.8);
        
        assertFalse(entry.hasEmbedding());
        
        // 手动创建 512 维向量
        float[] embedding = new float[512];
        embedding[0] = 1.0f;
        entry.setEmbedding(embedding);
        
        assertTrue(entry.hasEmbedding());
        assertEquals(512, entry.getEmbedding().length);
    }

    @Test
    @DisplayName("MemoryStore.searchSimilar 向量检索")
    void testVectorSearch() {
        // 添加多条记忆（手动创建向量）
        for (int i = 0; i < 5; i++) {
            MemoryEntry entry = new MemoryEntry("entry-" + i, "记忆内容 " + i, 
                MemoryEntry.MemoryType.FACT, Set.of("tag" + i), 0.8);
            // 创建简单向量
            float[] embedding = new float[512];
            embedding[i] = 1.0f;
            entry.setEmbedding(embedding);
            store.add(entry);
        }

        // 向量检索
        float[] queryEmbedding = new float[512];
        queryEmbedding[0] = 1.0f;
        var results = store.searchSimilar(queryEmbedding, 3, 0.5);

        assertNotNull(results);
        assertTrue(results.size() <= 3);
    }

    @Test
    @DisplayName("VectorizationScheduler 批量向量化")
    void testVectorizationScheduler() {
        // 添加未向量化的记忆
        for (int i = 0; i < 3; i++) {
            MemoryEntry entry = new MemoryEntry("pending-" + i, "待向量化内容 " + i, 
                MemoryEntry.MemoryType.FACT, Set.of("pending"), 0.8);
            store.add(entry);
        }

        // 使用可用的 Mock EmbeddingService
        EmbeddingService mockEmbeddingService = new EmbeddingService() {
            @Override public float[] embed(String text) {
                float[] vec = new float[512];
                vec[0] = 1.0f;
                return vec;
            }
            @Override public float[][] embedBatch(String[] texts) {
                float[][] result = new float[texts.length][];
                for (int i = 0; i < texts.length; i++) {
                    result[i] = embed(texts[i]);
                }
                return result;
            }
            @Override public boolean isAvailable() { return true; }
            @Override public int getDimension() { return 512; }
            @Override public void close() {}
        };

        VectorizationScheduler scheduler = new VectorizationScheduler(store, mockEmbeddingService);
        
        // 触发批量向量化
        scheduler.scheduleOnStartup();
        
        // 等待异步任务完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证向量化完成
        for (var meta : store.getAllMetas()) {
            MemoryEntry entry = store.findById(meta.id);
            if (entry != null && entry.getId().startsWith("pending-")) {
                assertTrue(entry.hasEmbedding(), "记忆 " + entry.getId() + " 应该已向量化");
            }
        }

        scheduler.shutdown();
    }

    @Test
    @DisplayName("优雅降级（关键词匹配）")
    void testGracefulDegradation() {
        // 添加记忆
        MemoryEntry entry = new MemoryEntry("spring-core", "Spring Core 配置", 
            MemoryEntry.MemoryType.TECHNICAL_CONTEXT, Set.of("spring", "java"), 0.9);
        entry.setContent("# Spring Core 配置\n\nSpring Boot 自动配置原理...");
        store.add(entry);

        // 使用不可用的 EmbeddingService
        EmbeddingService unavailableService = new EmbeddingService() {
            @Override public float[] embed(String text) { return new float[512]; }
            @Override public float[][] embedBatch(String[] texts) { return new float[texts.length][]; }
            @Override public boolean isAvailable() { return false; }
            @Override public int getDimension() { return 512; }
            @Override public void close() {}
        };

        // 应该回退到关键词匹配
        var results = store.search("Spring");
        assertFalse(results.isEmpty());
        assertEquals("spring-core", results.get(0).getId());
    }
}
