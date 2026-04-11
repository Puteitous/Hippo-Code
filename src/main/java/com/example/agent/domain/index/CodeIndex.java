package com.example.agent.domain.index;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CodeIndex {

    private static final Logger logger = LoggerFactory.getLogger(CodeIndex.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.IndexConfig config;
    private CodeSearchStrategy searchEngine;
    private final Map<String, CachedSearchResult> searchCache;

    public CodeIndex(TokenEstimator tokenEstimator, ContextConfig.IndexConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig.IndexConfig();
        this.searchEngine = SearchEngineFactory.getDefault();
        this.searchCache = new ConcurrentHashMap<>();
    }

    public CodeIndex(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    public void setSearchEngine(CodeSearchStrategy searchEngine) {
        this.searchEngine = searchEngine;
        searchCache.clear();
        logger.info("检索引擎已切换为: {}", searchEngine.getClass().getSimpleName());
    }

    public void buildIndex() {
        long startTime = System.currentTimeMillis();
        searchEngine.buildIndex();
        logger.info("倒排索引构建完成，共索引 {} 个文件 (耗时: {}ms)",
                searchEngine.getIndexSize(), System.currentTimeMillis() - startTime);
    }

    public List<String> search(String query, int maxResults, int maxTokens) {
        List<String> formattedResults = new ArrayList<>();

        if (query == null) {
            logger.debug("查询为 null，返回空结果");
            return formattedResults;
        }
        if (maxResults <= 0 || maxTokens <= 0) {
            logger.debug("参数无效 maxResults={}, maxTokens={}，返回空结果", maxResults, maxTokens);
            return formattedResults;
        }

        long startTime = System.currentTimeMillis();

        CachedSearchResult cached = searchCache.get(query);
        if (cached != null && !cached.isExpired()) {
            logger.debug("检索缓存命中: '{}' (耗时: {}ms)", query, System.currentTimeMillis() - startTime);
            return cached.results;
        }

        List<SearchResult> results = searchEngine.search(query, maxResults);
        if (results.isEmpty()) {
            logger.debug("检索无结果: '{}'", query);
            return formattedResults;
        }

        int totalTokens = 0;
        for (SearchResult result : results) {
            StringBuilder item = new StringBuilder();
            item.append("📄 ").append(result.filePath).append("\n");
            item.append("   相关性: ").append(String.format("%.0f%%", result.score * 100)).append("\n");
            item.append("   摘要:\n");

            String preview = result.preview;
            int resultTokens = tokenEstimator.estimateTextTokens(preview);

            if (totalTokens + resultTokens > maxTokens) {
                logger.debug("达到 token 上限 {}，截断结果", maxTokens);
                break;
            }

            item.append(preview).append("\n");
            formattedResults.add(item.toString());
            totalTokens += resultTokens;
        }

        searchCache.put(query, new CachedSearchResult(formattedResults, 30 * 60 * 1000L));
        logger.debug("检索完成: '{}' 返回 {} 个结果，约 {} tokens (耗时: {}ms)",
                query, formattedResults.size(), totalTokens, System.currentTimeMillis() - startTime);

        return formattedResults;
    }

    public void cleanupCache() {
        int removed = 0;
        for (Map.Entry<String, CachedSearchResult> entry : searchCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                searchCache.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.debug("清理了 {} 个过期的检索缓存", removed);
        }
    }

    public void clearCache() {
        searchCache.clear();
        logger.debug("清空所有检索缓存");
    }

    public int getCacheSize() {
        return searchCache.size();
    }

    private static class CachedSearchResult {
        final List<String> results;
        final long expireTime;

        CachedSearchResult(List<String> results, long ttlMillis) {
            this.results = results;
            this.expireTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
