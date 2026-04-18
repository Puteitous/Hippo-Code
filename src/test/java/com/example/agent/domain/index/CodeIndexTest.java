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
    @DisplayName("构造函数 - null config 使用默认配置")
    void testConstructorWithNullConfig() {
        CodeIndex index = new CodeIndex(tokenEstimator, null);
        assertNotNull(index);
    }

    @Test
    @DisplayName("构造函数 - null tokenEstimator 应抛出 IllegalArgumentException (Fail-Fast)")
    void testConstructorWithNullTokenEstimator() {
        IndexConfig config = new IndexConfig();
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new CodeIndex(null, config)
        );
        assertEquals("TokenEstimator cannot be null", e.getMessage());
    }

    @Test
    @DisplayName("构造函数 - 双参数都为 null 应抛出异常")
    void testConstructorWithBothNull() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new CodeIndex(null, null)
        );
        assertEquals("TokenEstimator cannot be null", e.getMessage());
    }

    @Test
    @DisplayName("构造函数 - 单参数 null tokenEstimator 应抛出异常")
    void testSingleArgConstructor() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new CodeIndex(null)
        );
        assertEquals("TokenEstimator cannot be null", e.getMessage());
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
    @DisplayName("边界 - 设置检索引擎不报错")
    void testSetSearchEngine() {
        assertDoesNotThrow(() -> codeIndex.setSearchEngine(SearchEngineFactory.keywordMatch()));
    }

    @Test
    @DisplayName("边界 - 设置 null 检索引擎（不推荐但能处理）")
    void testSetNullSearchEngine() {
        assertDoesNotThrow(() -> codeIndex.setSearchEngine(null));
    }
}

