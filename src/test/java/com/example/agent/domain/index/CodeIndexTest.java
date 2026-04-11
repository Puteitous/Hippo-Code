package com.example.agent.domain.index;

import com.example.agent.config.IndexConfig;
import com.example.agent.service.SimpleTokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeIndex 边界条件测试
 *
 * 测试重点：
 * - null 查询参数
 * - 负数/零值参数边界
 * - 空结果处理
 * - 缓存清理边界
 */
class CodeIndexTest {

    private CodeIndex codeIndex;
    private SimpleTokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        codeIndex = new CodeIndex(tokenEstimator);
    }

    @Test
    @DisplayName("构造函数 - null config")
    void testConstructorWithNullConfig() {
        CodeIndex index = new CodeIndex(tokenEstimator, null);
        assertNotNull(index);
        assertEquals(0, index.getCacheSize());
    }

    @Test
    @DisplayName("构造函数 - null tokenEstimator")
    void testConstructorWithNullTokenEstimator() {
        IndexConfig config = new IndexConfig();
        assertDoesNotThrow(() -> new CodeIndex(null, config));
    }

    @Test
    @DisplayName("构造函数 - 双参数都为 null")
    void testConstructorWithBothNull() {
        assertDoesNotThrow(() -> new CodeIndex(null, null));
    }

    @Test
    @DisplayName("构造函数 - 单参数 null tokenEstimator")
    void testSingleArgConstructor() {
        assertDoesNotThrow(() -> new CodeIndex(null));
    }

    @Test
    @DisplayName("边界 - buildIndex 不报错")
    void testBuildIndex() {
        assertDoesNotThrow(() -> codeIndex.buildIndex());
    }

    @Test
    @DisplayName("边界 - null 查询返回空列表")
    void testSearchWithNullQuery() {
        List<String> results = codeIndex.search(null, 5, 1000);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("边界 - maxResults = 0 返回空列表")
    void testSearchWithZeroMaxResults() {
        List<String> results = codeIndex.search("test", 0, 1000);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("边界 - maxResults 为负数返回空列表")
    void testSearchWithNegativeMaxResults() {
        List<String> results = codeIndex.search("test", -5, 1000);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("边界 - maxTokens = 0 返回空列表")
    void testSearchWithZeroMaxTokens() {
        List<String> results = codeIndex.search("test", 5, 0);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("边界 - maxTokens 为负数返回空列表")
    void testSearchWithNegativeMaxTokens() {
        List<String> results = codeIndex.search("test", 5, -100);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("边界 - 所有参数都无效")
    void testSearchWithAllInvalidParams() {
        List<String> results = codeIndex.search(null, -1, -1);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("边界 - 空字符串查询")
    void testSearchWithEmptyQuery() {
        List<String> results = codeIndex.search("", 5, 1000);
        assertNotNull(results);
    }

    @Test
    @DisplayName("边界 - 非常大的 maxResults")
    void testSearchWithVeryLargeMaxResults() {
        List<String> results = codeIndex.search("test", Integer.MAX_VALUE, 1000);
        assertNotNull(results);
    }

    @Test
    @DisplayName("边界 - 非常大的 maxTokens")
    void testSearchWithVeryLargeMaxTokens() {
        List<String> results = codeIndex.search("test", 5, Integer.MAX_VALUE);
        assertNotNull(results);
    }

    @Test
    @DisplayName("边界 - 多次相同查询使用缓存")
    void testSearchCaching() {
        String query = "cache_test_query_xyz";
        int before = codeIndex.getCacheSize();

        codeIndex.search(query, 5, 1000);
        int afterFirst = codeIndex.getCacheSize();

        codeIndex.search(query, 5, 1000);
        int afterSecond = codeIndex.getCacheSize();

        assertTrue(afterFirst > before);
        assertEquals(afterFirst, afterSecond);
    }

    @Test
    @DisplayName("边界 - 清理缓存不报错")
    void testCleanupCache() {
        codeIndex.search("test1", 5, 1000);
        codeIndex.search("test2", 5, 1000);

        assertDoesNotThrow(() -> codeIndex.cleanupCache());
    }

    @Test
    @DisplayName("边界 - 清空所有缓存")
    void testClearCache() {
        codeIndex.search("test1", 5, 1000);
        codeIndex.search("test2", 5, 1000);
        assertTrue(codeIndex.getCacheSize() > 0);

        codeIndex.clearCache();
        assertEquals(0, codeIndex.getCacheSize());
    }

    @Test
    @DisplayName("边界 - 空缓存操作不报错")
    void testEmptyCacheOperations() {
        assertEquals(0, codeIndex.getCacheSize());
        assertDoesNotThrow(() -> codeIndex.cleanupCache());
        assertDoesNotThrow(() -> codeIndex.clearCache());
    }

    @Test
    @DisplayName("边界 - 设置检索引擎不报错")
    void testSetSearchEngine() {
        assertDoesNotThrow(() -> codeIndex.setSearchEngine(SearchEngineFactory.keywordMatch()));
    }

    @Test
    @DisplayName("边界 - 设置 null 检索引擎（不推荐但能处理）")
    void testSetNullSearchEngine() {
        assertDoesNotThrow(() -> codeIndex.setSearchEngine(null));
    }

    @Test
    @DisplayName("边界 - 连续多次构建索引")
    void testMultipleBuildIndex() {
        assertDoesNotThrow(() -> {
            codeIndex.buildIndex();
            codeIndex.buildIndex();
            codeIndex.buildIndex();
        });
    }

    @Test
    @DisplayName("边界 - 构建索引后查询")
    void testBuildThenSearch() {
        codeIndex.buildIndex();
        List<String> results = codeIndex.search("java", 3, 1000);
        assertNotNull(results);
    }
}
