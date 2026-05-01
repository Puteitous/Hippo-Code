package com.example.agent.memory;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 记忆系统监控指标收集器测试
 */
@DisplayName("记忆系统监控指标收集器测试")
class MemoryMetricsCollectorTest {

    private MemoryMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MemoryMetricsCollector();
    }

    @Test
    @DisplayName("记录 Embedding 调用耗时")
    void testRecordEmbedding() {
        collector.recordEmbedding(50);
        collector.recordEmbedding(100);
        collector.recordEmbedding(75);

        assertEquals(3, collector.getSummary().contains("Embedding 调用: 3 次") ? 3 : 0);
        assertEquals(75.0, collector.getAverageEmbeddingTime(), 1.0);
        assertTrue(collector.getSummary().contains("最大耗时: 100 ms"));
    }

    @Test
    @DisplayName("记录向量检索命中率")
    void testVectorSearchHitRate() {
        collector.recordVectorSearchHit();
        collector.recordVectorSearchHit();
        collector.recordVectorSearchMiss();

        assertEquals(2.0 / 3.0, collector.getVectorSearchHitRate(), 0.01);
        assertEquals(1, collector.getSummary().contains("降级次数: 1") ? 1 : 0);
    }

    @Test
    @DisplayName("记录降级率")
    void testFallbackRate() {
        collector.recordVectorSearchHit();
        collector.recordVectorSearchMiss();
        collector.recordVectorSearchMiss();

        assertEquals(2.0 / 3.0, collector.getFallbackRate(), 0.01);
    }

    @Test
    @DisplayName("记录注入成功率")
    void testInjectionSuccessRate() {
        collector.recordInjectionSuccess();
        collector.recordInjectionSuccess();
        collector.recordInjectionEmpty();

        assertEquals(2.0 / 3.0, collector.getInjectionSuccessRate(), 0.01);
    }

    @Test
    @DisplayName("生成指标摘要")
    void testGetSummary() {
        collector.recordEmbedding(50);
        collector.recordEmbedding(100);
        collector.recordVectorSearchHit();
        collector.recordVectorSearchMiss();
        collector.recordInjectionSuccess();
        collector.recordInjectionEmpty();

        String summary = collector.getSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("记忆系统指标"));
        assertTrue(summary.contains("Embedding 调用: 2 次"));
        assertTrue(summary.contains("向量检索: 2 次"));
        assertTrue(summary.contains("降级次数: 1"));
        assertTrue(summary.contains("记忆注入: 1 次成功, 1 次为空"));
    }

    @Test
    @DisplayName("重置指标")
    void testReset() {
        collector.recordEmbedding(50);
        collector.recordVectorSearchHit();
        collector.recordInjectionSuccess();

        collector.reset();

        assertEquals(0.0, collector.getAverageEmbeddingTime());
        assertEquals(0.0, collector.getVectorSearchHitRate());
        assertEquals(0.0, collector.getInjectionSuccessRate());
    }

    @Test
    @DisplayName("空指标状态")
    void testEmptyState() {
        assertEquals(0.0, collector.getAverageEmbeddingTime());
        assertEquals(0.0, collector.getVectorSearchHitRate());
        assertEquals(0.0, collector.getFallbackRate());
        assertEquals(0.0, collector.getInjectionSuccessRate());
    }
}
