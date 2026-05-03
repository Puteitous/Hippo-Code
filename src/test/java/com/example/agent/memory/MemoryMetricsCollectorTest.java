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
    @DisplayName("记录检索命中率")
    void testSearchHitRate() {
        collector.recordVectorSearchHit();
        collector.recordVectorSearchHit();
        collector.recordVectorSearchMiss();

        assertEquals(2.0 / 3.0, collector.getSearchHitRate(), 0.01);
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
        collector.recordVectorSearchHit();
        collector.recordVectorSearchMiss();
        collector.recordInjectionSuccess();
        collector.recordInjectionEmpty();

        String summary = collector.getSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("记忆系统指标"));
        assertTrue(summary.contains("检索: 2 次"));
        assertTrue(summary.contains("降级次数: 1"));
        assertTrue(summary.contains("记忆注入: 1 次成功, 1 次为空"));
    }

    @Test
    @DisplayName("重置指标")
    void testReset() {
        collector.recordVectorSearchHit();
        collector.recordInjectionSuccess();

        collector.reset();

        assertEquals(0.0, collector.getSearchHitRate());
        assertEquals(0.0, collector.getInjectionSuccessRate());
    }

    @Test
    @DisplayName("空指标状态")
    void testEmptyState() {
        assertEquals(0.0, collector.getSearchHitRate());
        assertEquals(0.0, collector.getFallbackRate());
        assertEquals(0.0, collector.getInjectionSuccessRate());
    }
}
