package com.example.agent.core.health;

import com.example.agent.domain.cache.CacheManager;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

public class CacheHealthIndicator implements HealthIndicator {
    private final CacheManager cacheManager;

    public CacheHealthIndicator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public String getName() {
        return "cache";
    }

    @Override
    public Health check() {
        try {
            CacheStats stats = cacheManager.getAggregatedStats();

            double hitRate = stats.hitRate();
            long hitCount = stats.hitCount();
            long missCount = stats.missCount();
            long evictionCount = stats.evictionCount();

            Health.Builder builder = Health.up()
                    .withDetail("hit_rate", String.format("%.2f%%", hitRate * 100))
                    .withDetail("hit_count", hitCount)
                    .withDetail("miss_count", missCount)
                    .withDetail("eviction_count", evictionCount)
                    .withDetail("total_load_time_ms", stats.totalLoadTime() / 1_000_000)
                    .withDetail("monitor_enabled", cacheManager.isMonitorRunning());

            if (hitRate < 0.5 && hitCount + missCount > 100) {
                return Health.degraded()
                        .withDetail("warning", "缓存命中率低于50%")
                        .withDetail("hit_rate", String.format("%.2f%%", hitRate * 100))
                        .build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
