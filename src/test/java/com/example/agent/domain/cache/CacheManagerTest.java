package com.example.agent.domain.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheManager 边界条件测试
 *
 * 测试重点：
 * - null 键值处理
 * - 过期机制边界
 * - 并发安全基础验证
 * - 空值/特殊值处理
 */
class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    @DisplayName("构造函数 - 默认 TTL 构造")
    void testDefaultConstructor() {
        CacheManager cm = new CacheManager();
        assertNotNull(cm);
        assertEquals(0, cm.size());
    }

    @Test
    @DisplayName("构造函数 - 自定义 TTL 构造")
    void testCustomTtlConstructor() {
        CacheManager cm = new CacheManager(5000);
        assertNotNull(cm);
        assertEquals(0, cm.size());
    }

    @Test
    @DisplayName("边界 - 获取不存在的 key 返回 null")
    void testGetNonExistentKey() {
        assertNull(cacheManager.get("nonexistent"));
    }

    @Test
    @DisplayName("边界 - null key 获取")
    void testGetNullKey() {
        assertNull(cacheManager.get(null));
    }

    @Test
    @DisplayName("边界 - 存入 null value")
    void testPutNullValue() {
        cacheManager.put("nullKey", null);
        assertNull(cacheManager.get("nullKey"));
    }

    @Test
    @DisplayName("边界 - null key 存入")
    void testPutNullKey() {
        cacheManager.put(null, "value");
        assertEquals(1, cacheManager.size());
    }

    @Test
    @DisplayName("边界 - 存入并正常获取")
    void testPutAndGet() {
        cacheManager.put("key1", "value1");
        assertEquals("value1", cacheManager.get("key1"));
        assertEquals(1, cacheManager.size());
    }

    @Test
    @DisplayName("边界 - 覆盖已有 key")
    void testPutOverwrite() {
        cacheManager.put("key1", "value1");
        cacheManager.put("key1", "value2");
        assertEquals("value2", cacheManager.get("key1"));
        assertEquals(1, cacheManager.size());
    }

    @Test
    @DisplayName("边界 - 手动失效缓存")
    void testInvalidate() {
        cacheManager.put("key1", "value1");
        cacheManager.invalidate("key1");
        assertNull(cacheManager.get("key1"));
        assertEquals(0, cacheManager.size());
    }

    @Test
    @DisplayName("边界 - 失效不存在的 key 不报错")
    void testInvalidateNonExistent() {
        assertDoesNotThrow(() -> cacheManager.invalidate("nonexistent"));
    }

    @Test
    @DisplayName("边界 - 失效 null key 不报错")
    void testInvalidateNull() {
        assertDoesNotThrow(() -> cacheManager.invalidate(null));
    }

    @Test
    @DisplayName("边界 - 清空所有缓存")
    void testClear() {
        cacheManager.put("key1", "value1");
        cacheManager.put("key2", "value2");
        assertEquals(2, cacheManager.size());

        cacheManager.clear();
        assertEquals(0, cacheManager.size());
        assertNull(cacheManager.get("key1"));
        assertNull(cacheManager.get("key2"));
    }

    @Test
    @DisplayName("边界 - 清理过期缓存")
    void testCleanup() {
        CacheManager shortTtlCache = new CacheManager(1);
        shortTtlCache.put("key1", "value1");
        assertEquals(1, shortTtlCache.size());

        assertDoesNotThrow(() -> shortTtlCache.cleanup());
    }

    @Test
    @DisplayName("边界 - 不同类型值缓存")
    void testDifferentTypes() {
        cacheManager.put("string", "hello");
        cacheManager.put("int", 42);
        cacheManager.put("bool", true);

        assertEquals("hello", cacheManager.get("string"));
        assertEquals(Integer.valueOf(42), cacheManager.get("int"));
        assertEquals(Boolean.valueOf(true), cacheManager.get("bool"));
        assertEquals(3, cacheManager.size());
    }

    @Test
    @DisplayName("边界 - 空字符串 key")
    void testEmptyStringKey() {
        cacheManager.put("", "emptyKey");
        assertEquals("emptyKey", cacheManager.get(""));
        assertEquals(1, cacheManager.size());
    }

    @Test
    @DisplayName("边界 - 极短 TTL 过期验证")
    void testVeryShortTtl() throws InterruptedException {
        CacheManager shortTtlCache = new CacheManager(1);
        shortTtlCache.put("key1", "value1", 1);

        Thread.sleep(10);
        assertNull(shortTtlCache.get("key1"));
    }

    @Test
    @DisplayName("边界 - 空缓存操作不报错")
    void testEmptyCacheOperations() {
        assertDoesNotThrow(() -> cacheManager.clear());
        assertDoesNotThrow(() -> cacheManager.cleanup());
        assertEquals(0, cacheManager.size());
    }
}
