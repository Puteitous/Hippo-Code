package com.example.agent.domain.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    private final Cache<String, String> fileCache;
    private final Cache<String, List<String>> searchCache;
    private final Cache<String, Object> commonCache;

    public CacheManager() {
        this.fileCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();

        this.searchCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();

        this.commonCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();

        logger.debug("CacheManager 初始化完成，3个缓存分区已就绪");
    }

    public String getFile(String key) {
        String value = fileCache.getIfPresent(key);
        logCacheHit("file", key, value != null);
        return value;
    }

    public void putFile(String key, String value) {
        fileCache.put(key, value);
        logger.debug("文件缓存存入: {} (大小: {} 字符)", key, value != null ? value.length() : 0);
    }

    public void putFile(String key, String value, long ttlSeconds) {
        putFile(key, value);
    }

    public List<String> getSearch(String key) {
        List<String> value = searchCache.getIfPresent(key);
        logCacheHit("search", key, value != null);
        return value;
    }

    public void putSearch(String key, List<String> value) {
        searchCache.put(key, value);
        logger.debug("检索缓存存入: {} ({} 个结果)", key, value != null ? value.size() : 0);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        T value = (T) commonCache.getIfPresent(key);
        logCacheHit("common", key, value != null);
        return value;
    }

    public <T> void put(String key, T value) {
        commonCache.put(key, value);
        logger.debug("通用缓存存入: {}", key);
    }

    public <T> void put(String key, T value, long ttlSeconds) {
        put(key, value);
    }

    public void invalidateFile(String key) {
        fileCache.invalidate(key);
        logger.debug("文件缓存失效: {}", key);
    }

    public void invalidateSearch(String key) {
        if (key == null) {
            searchCache.invalidateAll();
            logger.debug("所有检索缓存已清空");
        } else {
            searchCache.invalidate(key);
            logger.debug("检索缓存失效: {}", key);
        }
    }

    public void invalidate(String key) {
        commonCache.invalidate(key);
        logger.debug("通用缓存失效: {}", key);
    }

    public void onFileChanged(String filePath) {
        fileCache.invalidate(filePath);
        searchCache.invalidateAll();
        logger.debug("文件变更，级联清理相关缓存: {}", filePath);
    }

    public void cleanup() {
        fileCache.cleanUp();
        searchCache.cleanUp();
        commonCache.cleanUp();
        logger.debug("所有缓存分区清理完成");
    }

    public void clear() {
        fileCache.invalidateAll();
        searchCache.invalidateAll();
        commonCache.invalidateAll();
        logger.debug("所有缓存分区已清空");
    }

    public int size() {
        return (int) (fileCache.estimatedSize() + searchCache.estimatedSize() + commonCache.estimatedSize());
    }

    public String getStats() {
        CacheStats fileStats = fileCache.stats();
        CacheStats searchStats = searchCache.stats();
        CacheStats commonStats = commonCache.stats();

        return String.format(
            "缓存统计 - 文件[数量: %d, 命中率: %.1f%%], 检索[数量: %d, 命中率: %.1f%%], 通用[数量: %d, 命中率: %.1f%%]",
            fileCache.estimatedSize(), fileStats.hitRate() * 100,
            searchCache.estimatedSize(), searchStats.hitRate() * 100,
            commonCache.estimatedSize(), commonStats.hitRate() * 100
        );
    }

    private void logCacheHit(String partition, String key, boolean hit) {
        if (hit) {
            logger.debug("{}缓存命中: {}", partition, key);
        } else {
            logger.debug("{}缓存未命中: {}", partition, key);
        }
    }
}
