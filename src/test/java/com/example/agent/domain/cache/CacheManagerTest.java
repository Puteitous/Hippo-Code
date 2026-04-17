package com.example.agent.domain.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    @DisplayName("构造函数 - 默认构造")
    void testDefaultConstructor() {
        CacheManager cm = new CacheManager();
        assertNotNull(cm);
        assertEquals(0, cm.size());
    }

    @Test
    @DisplayName("文件缓存 - 存入并正常获取")
    void testFileCachePutAndGet() {
        cacheManager.putFile("/path/to/file.java", "public class Test {}");
        assertEquals("public class Test {}", cacheManager.getFile("/path/to/file.java"));
        assertEquals(1, cacheManager.size());
    }

    @Test
    @DisplayName("文件缓存 - 获取不存在的文件返回 null")
    void testGetNonExistentFile() {
        assertNull(cacheManager.getFile("/nonexistent.java"));
    }

    @Test
    @DisplayName("文件缓存 - 手动失效缓存")
    void testInvalidateFile() {
        cacheManager.putFile("/path/to/file.java", "content");
        cacheManager.invalidateFile("/path/to/file.java");
        assertNull(cacheManager.getFile("/path/to/file.java"));
        assertEquals(0, cacheManager.size());
    }

    @Test
    @DisplayName("检索缓存 - 存入并正常获取")
    void testSearchCachePutAndGet() {
        cacheManager.putSearch("com.example", "query", List.of("result1", "result2"));
        assertEquals(List.of("result1", "result2"), cacheManager.getSearch("com.example", "query"));
        assertEquals(1, cacheManager.size());
    }

    @Test
    @DisplayName("检索缓存 - 获取不存在的返回 null")
    void testGetNonExistentSearch() {
        assertNull(cacheManager.getSearch("com.example", "nonexistent"));
    }

    @Test
    @DisplayName("检索缓存 - 按包名失效")
    void testInvalidateSearchByPackage() {
        cacheManager.putSearch("com.example", "query1", List.of("a"));
        cacheManager.putSearch("com.other", "query2", List.of("b"));
        cacheManager.invalidateSearch("com.example");
        assertNull(cacheManager.getSearch("com.example", "query1"));
        assertEquals(List.of("b"), cacheManager.getSearch("com.other", "query2"));
    }

    @Test
    @DisplayName("检索缓存 - 清空所有")
    void testInvalidateAllSearch() {
        cacheManager.putSearch("com.example", "query1", List.of("a"));
        cacheManager.putSearch("com.other", "query2", List.of("b"));
        cacheManager.invalidateSearch(null);
        assertNull(cacheManager.getSearch("com.example", "query1"));
        assertNull(cacheManager.getSearch("com.other", "query2"));
        assertEquals(0, cacheManager.size());
    }

    @Test
    @DisplayName("文件变更 - 级联清理文件缓存")
    void testOnFileChangedInvalidatesFileCache() {
        cacheManager.putFile("/project/src/java/com/example/Test.java", "content");
        cacheManager.onFileChanged("/project/src/java/com/example/Test.java");
        assertNull(cacheManager.getFile("/project/src/java/com/example/Test.java"));
    }

    @Test
    @DisplayName("文件变更 - 级联清理同包检索缓存")
    void testOnFileChangedInvalidatesSearchCache() {
        cacheManager.putSearch("com.example", "query", List.of("result"));
        cacheManager.onFileChanged("/project/src/java/com/example/Test.java");
        assertNull(cacheManager.getSearch("com.example", "query"));
    }

    @Test
    @DisplayName("清空所有缓存")
    void testClear() {
        cacheManager.putFile("/path/to/file.java", "content");
        cacheManager.putSearch("com.example", "query", List.of("result"));
        assertEquals(2, cacheManager.size());

        cacheManager.clear();
        assertEquals(0, cacheManager.size());
        assertNull(cacheManager.getFile("/path/to/file.java"));
        assertNull(cacheManager.getSearch("com.example", "query"));
    }

    @Test
    @DisplayName("清理过期缓存不报错")
    void testCleanup() {
        cacheManager.putFile("/path/to/file.java", "content");
        assertEquals(1, cacheManager.size());
        assertDoesNotThrow(() -> cacheManager.cleanup());
    }

    @Test
    @DisplayName("空缓存操作不报错")
    void testEmptyCacheOperations() {
        assertDoesNotThrow(() -> cacheManager.clear());
        assertDoesNotThrow(() -> cacheManager.cleanup());
        assertEquals(0, cacheManager.size());
    }

    @Test
    @DisplayName("获取统计信息")
    void testGetStats() {
        String stats = cacheManager.getStats();
        assertNotNull(stats);
        assertTrue(stats.contains("文件"));
        assertTrue(stats.contains("检索"));
    }

    @Test
    @DisplayName("获取聚合统计")
    void testGetAggregatedStats() {
        assertNotNull(cacheManager.getAggregatedStats());
    }

    @Test
    @DisplayName("监控初始状态未运行")
    void testMonitorNotRunningInitially() {
        assertFalse(cacheManager.isMonitorRunning());
    }

    @Test
    @DisplayName("启动内存监控")
    void testStartMemoryMonitor() {
        cacheManager.startMemoryMonitor();
        assertTrue(cacheManager.isMonitorRunning());
    }

    @Test
    @DisplayName("内存使用率初始值为0")
    void testGetLastMemoryUsage() {
        assertEquals(0.0, cacheManager.getLastMemoryUsage());
    }
}
