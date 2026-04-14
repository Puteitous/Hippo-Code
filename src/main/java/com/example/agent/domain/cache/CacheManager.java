package com.example.agent.domain.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.example.agent.core.concurrency.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    public enum CacheCost {
        SEARCH(10, "检索结果"),
        AST(20, "语法树"),
        COMMON(30, "通用对象"),
        FILE(100, "文件内容");

        final int weight;
        final String desc;

        CacheCost(int weight, String desc) {
            this.weight = weight;
            this.desc = desc;
        }

        public int getWeight() {
            return weight;
        }
    }

    private static class CachePartition<K, V> {
        final Cache<K, V> cache;
        final CacheCost cost;
        final String name;

        CachePartition(String name, CacheCost cost, Cache<K, V> cache) {
            this.name = name;
            this.cost = cost;
            this.cache = cache;
        }
    }

    private static class AstEntry {
        final Object tree;
        final long version;
        final String language;

        AstEntry(Object tree, long version, String language) {
            this.tree = tree;
            this.version = version;
            this.language = language;
        }
    }

    private final CachePartition<String, String> fileCache;
    private final CachePartition<String, AstEntry> astCache;
    private final CachePartition<String, Map<String, List<String>>> searchCache;
    private final CachePartition<String, Object> commonCache;
    private final List<CachePartition<?, ?>> allPartitions;
    private final long defaultTtlMillis;
    private ScheduledExecutorService monitorExecutor;
    private volatile double lastMemoryUsage;

    private static class InstanceHolder {
        private static final CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    CacheManager() {
        this(30 * 60 * 1000L);
    }

    CacheManager(long defaultTtlMillis) {
        this.defaultTtlMillis = defaultTtlMillis;

        this.searchCache = new CachePartition<>("search", CacheCost.SEARCH,
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build());

        this.astCache = new CachePartition<>("ast", CacheCost.AST,
            Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build());

        this.commonCache = new CachePartition<>("common", CacheCost.COMMON,
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(defaultTtlMillis, TimeUnit.MILLISECONDS)
                .recordStats()
                .build());

        this.fileCache = new CachePartition<>("file", CacheCost.FILE,
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build());

        this.allPartitions = new ArrayList<>();
        allPartitions.add(searchCache);
        allPartitions.add(astCache);
        allPartitions.add(commonCache);
        allPartitions.add(fileCache);
        allPartitions.sort(Comparator.comparingInt(p -> p.cost.getWeight()));

        this.monitorExecutor = null;
        this.lastMemoryUsage = 0.0;

        logger.debug("CacheManager 初始化完成，4分区按成本排序: {}",
            allPartitions.stream().map(p -> p.name + "(" + p.cost.getWeight() + ")").toList());
    }

    public void startMemoryMonitor() {
        monitorExecutor = ThreadPools.cacheMonitor();
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                checkMemoryPressure();
            } catch (OutOfMemoryError e) {
                logger.error("缓存监控线程检测到内存溢出，执行紧急清理: {}", e.getMessage());
                clearPartitionsUpTo(CacheCost.FILE);
            } catch (Throwable t) {
                logger.error("缓存监控线程异常: {}", t.getMessage(), t);
            }
        }, 30, 30, TimeUnit.SECONDS);

        logger.debug("内存监控线程已启动，每30秒检查一次");
    }

    private void checkMemoryPressure() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        double usage = (double) used / runtime.maxMemory();
        lastMemoryUsage = usage;

        int cleared = 0;
        if (usage > 0.95) {
            logger.warn("内存压力极高 ({:.0f}%)，执行四级清理", usage * 100);
            cleared = clearPartitionsUpTo(CacheCost.FILE);
        } else if (usage > 0.90) {
            logger.info("内存压力很高 ({:.0f}%)，执行三级清理", usage * 100);
            cleared = clearPartitionsUpTo(CacheCost.COMMON);
        } else if (usage > 0.85) {
            logger.info("内存压力较高 ({:.0f}%)，执行二级清理", usage * 100);
            cleared = clearPartitionsUpTo(CacheCost.AST);
        } else if (usage > 0.80) {
            logger.info("内存压力适中 ({:.0f}%)，执行一级清理", usage * 100);
            cleared = clearPartitionsUpTo(CacheCost.SEARCH);
        } else if (usage > 0.70) {
            logger.debug("内存使用率: {:.0f}%，清理所有过期项", usage * 100);
            cleanup();
        }

        if (cleared > 0) {
            logger.info("已清理 {} 个缓存分区，当前缓存统计: {}", cleared, getStats());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("缓存统计: {}", getStats());
        }
    }

    private int clearPartitionsUpTo(CacheCost maxCost) {
        int count = 0;
        for (CachePartition<?, ?> partition : allPartitions) {
            if (partition.cost.getWeight() <= maxCost.getWeight()) {
                partition.cache.invalidateAll();
                logger.info("已清理 {} 缓存 (成本权重: {})", partition.name, partition.cost.getWeight());
                count++;
            }
        }
        return count;
    }

    public double getLastMemoryUsage() {
        return lastMemoryUsage;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAst(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return null;
        }

        AstEntry entry = astCache.cache.getIfPresent(filePath);
        if (entry == null) {
            logCacheHit("ast", filePath, false);
            return null;
        }

        try {
            long currentVersion = Files.getLastModifiedTime(path).toMillis();

            if (entry.version == currentVersion) {
                logCacheHit("ast", filePath, true);
                return (T) entry.tree;
            } else {
                logger.debug("AST版本过期: {} (缓存: {}, 当前: {})",
                    filePath, entry.version, currentVersion);
                astCache.cache.invalidate(filePath);
                return null;
            }
        } catch (IOException e) {
            logger.debug("读文件版本失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    public void putAst(String filePath, Object astTree, String language) {
        try {
            Path path = Paths.get(filePath);
            long version = Files.getLastModifiedTime(path).toMillis();
            astCache.cache.put(filePath, new AstEntry(astTree, version, language));
            logger.debug("AST缓存存入: {} (语言: {}, 版本: {})", filePath, language, version);
        } catch (IOException e) {
            logger.debug("存AST缓存失败: {} - {}", filePath, e.getMessage());
        }
    }

    public String getFile(String key) {
        String value = fileCache.cache.getIfPresent(key);
        logCacheHit("file", key, value != null);
        return value;
    }

    public void putFile(String key, String value) {
        fileCache.cache.put(key, value);
        logger.debug("文件缓存存入: {} (大小: {} 字符)", key, value != null ? value.length() : 0);
    }

    public void putFile(String key, String value, long ttlSeconds) {
        putFile(key, value);
    }

    public List<String> getSearch(String packageName, String query) {
        Map<String, List<String>> packageMap = searchCache.cache.getIfPresent(packageName);
        if (packageMap != null) {
            List<String> result = packageMap.get(query);
            logCacheHit("search", packageName + ":" + query, result != null);
            return result;
        }
        return null;
    }

    public void putSearch(String packageName, String query, List<String> value) {
        Map<String, List<String>> packageMap = searchCache.cache.getIfPresent(packageName);
        if (packageMap == null) {
            packageMap = new java.util.concurrent.ConcurrentHashMap<>();
            searchCache.cache.put(packageName, packageMap);
        }
        packageMap.put(query, value);
        logger.debug("检索缓存存入: {}:{} ({} 个结果)", packageName, query, value != null ? value.size() : 0);
    }

    private static final String NULL_KEY = "__NULL_KEY__";

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        String actualKey = key != null ? key : NULL_KEY;
        T value = (T) commonCache.cache.getIfPresent(actualKey);
        logCacheHit("common", key, value != null);
        return value;
    }

    public <T> void put(String key, T value) {
        if (value == null) {
            return;
        }
        String actualKey = key != null ? key : NULL_KEY;
        commonCache.cache.put(actualKey, value);
        logger.debug("通用缓存存入: {}", key);
    }

    public <T> void put(String key, T value, long ttlSeconds) {
        put(key, value);
    }

    public void invalidateFile(String key) {
        fileCache.cache.invalidate(key);
        logger.debug("文件缓存失效: {}", key);
    }

    public void invalidateSearch(String packageName) {
        if (packageName == null) {
            searchCache.cache.invalidateAll();
            logger.debug("所有检索缓存已清空");
        } else {
            searchCache.cache.invalidate(packageName);
            logger.debug("检索缓存按包失效: {}", packageName);
        }
    }

    public void invalidate(String key) {
        String actualKey = key != null ? key : NULL_KEY;
        commonCache.cache.invalidate(actualKey);
        logger.debug("通用缓存失效: {}", key);
    }

    public void onFileChanged(String filePath) {
        fileCache.cache.invalidate(filePath);
        astCache.cache.invalidate(filePath);

        String packageName = extractPackageName(filePath);
        if (packageName != null) {
            searchCache.cache.invalidate(packageName);
            logger.debug("文件变更，级联清理缓存: {} (包: {})", filePath, packageName);
        } else {
            searchCache.cache.invalidateAll();
            logger.debug("文件变更，级联清理所有缓存: {}", filePath);
        }
    }

    private String extractPackageName(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();

            if (!fileName.endsWith(".java")) {
                return null;
            }

            Path javaPath = path;
            while (javaPath != null) {
                if (javaPath.getFileName() != null &&
                    javaPath.getFileName().toString().equals("java") &&
                    javaPath.getParent() != null &&
                    javaPath.getParent().getFileName() != null &&
                    javaPath.getParent().getFileName().toString().equals("src")) {
                    break;
                }
                javaPath = javaPath.getParent();
            }

            if (javaPath != null && path.startsWith(javaPath)) {
                Path relative = javaPath.relativize(path.getParent());
                return relative.toString().replace(javaPath.getFileSystem().getSeparator(), ".");
            }
        } catch (Exception e) {
            logger.debug("提取包名失败: {} - {}", filePath, e.getMessage());
        }
        return null;
    }

    public void cleanup() {
        fileCache.cache.cleanUp();
        astCache.cache.cleanUp();
        searchCache.cache.cleanUp();
        commonCache.cache.cleanUp();
    }

    public void clear() {
        fileCache.cache.invalidateAll();
        astCache.cache.invalidateAll();
        searchCache.cache.invalidateAll();
        commonCache.cache.invalidateAll();
        logger.debug("所有缓存分区已清空");
    }

    public int size() {
        return (int) (fileCache.cache.estimatedSize() +
            astCache.cache.estimatedSize() +
            searchCache.cache.estimatedSize() +
            commonCache.cache.estimatedSize());
    }

    public String getStats() {
        CacheStats fileStats = fileCache.cache.stats();
        CacheStats astStats = astCache.cache.stats();
        CacheStats searchStats = searchCache.cache.stats();
        CacheStats commonStats = commonCache.cache.stats();

        return String.format(
            "文件[%d, %.1f%%], AST[%d, %.1f%%], 检索[%d包, %.1f%%], 通用[%d, %.1f%%]",
            fileCache.cache.estimatedSize(), fileStats.hitRate() * 100,
            astCache.cache.estimatedSize(), astStats.hitRate() * 100,
            searchCache.cache.estimatedSize(), searchStats.hitRate() * 100,
            commonCache.cache.estimatedSize(), commonStats.hitRate() * 100
        );
    }

    public CacheStats getAggregatedStats() {
        return allPartitions.stream()
                .map(p -> p.cache.stats())
                .reduce(CacheStats.empty(), CacheStats::plus);
    }

    public boolean isMonitorRunning() {
        return monitorExecutor != null && !monitorExecutor.isShutdown();
    }



    private void logCacheHit(String partition, String key, boolean hit) {
        if (hit) {
            logger.debug("{}缓存命中: {}", partition, key);
        } else {
            logger.debug("{}缓存未命中: {}", partition, key);
        }
    }
}
