package com.example.agent.domain.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    private final Map<String, CacheEntry<?>> cache;
    private final long defaultTtlMillis;

    public CacheManager() {
        this(30 * 60 * 1000L);
    }

    public CacheManager(long defaultTtlMillis) {
        this.cache = new ConcurrentHashMap<>();
        this.defaultTtlMillis = defaultTtlMillis;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            logger.debug("缓存过期，自动移除: {}", key);
            return null;
        }
        logger.debug("缓存命中: {}", key);
        return (T) entry.value;
    }

    public <T> void put(String key, T value) {
        put(key, value, defaultTtlMillis / 1000);
    }

    public <T> void put(String key, T value, long ttlSeconds) {
        cache.put(key, new CacheEntry<>(value, ttlSeconds * 1000L));
        logger.debug("存入缓存: {} (TTL: {}s)", key, ttlSeconds);
    }

    public void invalidate(String key) {
        cache.remove(key);
        logger.debug("缓存失效: {}", key);
    }

    public void cleanup() {
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
        int removed = before - cache.size();
        if (removed > 0) {
            logger.debug("清理了 {} 个过期缓存项", removed);
        }
    }

    public void clear() {
        cache.clear();
        logger.debug("清空所有缓存");
    }

    public int size() {
        return cache.size();
    }

    private static class CacheEntry<T> {
        final T value;
        final long expireTime;

        CacheEntry(T value, long ttlMillis) {
            this.value = value;
            this.expireTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
