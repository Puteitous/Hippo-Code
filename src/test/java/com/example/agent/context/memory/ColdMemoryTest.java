package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ColdMemory 测试
 * 
 * 边界条件测试重点：
 * - null/空值参数
 * - 负数/零值参数
 * - 极端参数边界
 * - 缓存过期/清理
 */
class ColdMemoryTest {

    private ColdMemory coldMemory;
    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        coldMemory = new ColdMemory(tokenEstimator);
    }

    @Test
    void testColdMemoryCreation() {
        assertNotNull(coldMemory);
        assertEquals(0, coldMemory.getCacheSize());
    }

    @Test
    void testSearchWithNullConfig() {
        ColdMemory memory = new ColdMemory(tokenEstimator, null);
        assertNotNull(memory);
        List<String> results = memory.search("test query", 3, 1000);
        assertNotNull(results);
    }

    @Test
    void testSearchNormalCase() {
        List<String> results = coldMemory.search("AgentContext", 3, 5000);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.size() <= 3);
    }

    @Test
    void testSearchWithNullQuery() {
        List<String> results = coldMemory.search(null, 3, 1000);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithEmptyQuery() {
        List<String> results = coldMemory.search("", 3, 1000);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchWithBlankQuery() {
        List<String> results = coldMemory.search("   ", 3, 1000);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchWithZeroMaxResults() {
        List<String> results = coldMemory.search("test", 0, 1000);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithNegativeMaxResults() {
        List<String> results = coldMemory.search("test", -1, 1000);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithMaxResultsGreaterThanAvailable() {
        List<String> results = coldMemory.search("test", 1000, 1000);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.size() < 1000);
    }

    @Test
    void testSearchWithZeroMaxTokens() {
        List<String> results = coldMemory.search("test", 3, 0);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithNegativeMaxTokens() {
        List<String> results = coldMemory.search("test", 3, -100);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithVerySmallMaxTokens() {
        List<String> results = coldMemory.search("test", 3, 10);
        assertNotNull(results);
        assertTrue(results.size() <= 1);
    }

    @Test
    void testSearchCache() {
        String query = "测试缓存查询";

        List<String> results1 = coldMemory.search(query, 3, 1000);
        int cacheSize1 = coldMemory.getCacheSize();
        assertTrue(cacheSize1 > 0);

        List<String> results2 = coldMemory.search(query, 3, 1000);
        int cacheSize2 = coldMemory.getCacheSize();

        assertEquals(cacheSize1, cacheSize2);
        assertEquals(results1.size(), results2.size());
    }

    @Test
    void testCleanupCacheOnlyRemovesExpired() {
        coldMemory.search("测试1", 3, 1000);
        coldMemory.search("测试2", 3, 1000);
        int cacheSizeBefore = coldMemory.getCacheSize();
        assertTrue(cacheSizeBefore > 0);

        // cleanupCache 只清理过期缓存，新缓存不应被清理
        coldMemory.cleanupCache();
        assertEquals(cacheSizeBefore, coldMemory.getCacheSize());
    }

    @Test
    void testClearCache() {
        coldMemory.search("测试1", 3, 1000);
        coldMemory.search("测试2", 3, 1000);
        assertTrue(coldMemory.getCacheSize() > 0);

        coldMemory.clearCache();
        assertEquals(0, coldMemory.getCacheSize());
    }

    @Test
    void testCleanupEmptyCache() {
        assertEquals(0, coldMemory.getCacheSize());
        assertDoesNotThrow(() -> coldMemory.cleanupCache());
        assertEquals(0, coldMemory.getCacheSize());
    }

    @Test
    void testClearEmptyCache() {
        assertEquals(0, coldMemory.getCacheSize());
        assertDoesNotThrow(() -> coldMemory.clearCache());
        assertEquals(0, coldMemory.getCacheSize());
    }

    @Test
    void testSearchWithNullTokenEstimator() {
        assertDoesNotThrow(() -> new ColdMemory(null));
    }

    @Test
    void testSearchResultTruncation() {
        List<String> results = coldMemory.search("AgentContext", 10, 50);
        assertNotNull(results);

        if (!results.isEmpty()) {
            String firstResult = results.get(0);
            assertNotNull(firstResult);
        }
    }

    @Test
    void testMultipleSearchesWithDifferentQueries() {
        for (int i = 0; i < 10; i++) {
            List<String> results = coldMemory.search("query" + i, 3, 1000);
            assertNotNull(results);
        }
        assertEquals(10, coldMemory.getCacheSize());
    }

    @Test
    void testSearchWithEmptyConfig() {
        ContextConfig.ColdMemoryConfig config = new ContextConfig.ColdMemoryConfig();
        ColdMemory memory = new ColdMemory(tokenEstimator, config);

        List<String> results = memory.search("test", 3, 1000);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }
}
