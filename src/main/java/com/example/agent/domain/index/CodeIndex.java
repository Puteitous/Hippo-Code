package com.example.agent.domain.index;

import com.example.agent.config.IndexConfig;
import com.example.agent.domain.cache.CacheManager;
import com.example.agent.domain.truncation.TruncationService;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CodeIndex {

    private static final Logger logger = LoggerFactory.getLogger(CodeIndex.class);

    private final TokenEstimator tokenEstimator;
    private final IndexConfig config;
    private CodeSearchStrategy searchEngine;
    private final CacheManager cacheManager;
    private final TruncationService truncationService;

    public CodeIndex(TokenEstimator tokenEstimator, IndexConfig config, CacheManager cacheManager) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new IndexConfig();
        this.searchEngine = SearchEngineFactory.getDefault();
        this.cacheManager = cacheManager;
        this.truncationService = new TruncationService(tokenEstimator);
    }

    public CodeIndex(TokenEstimator tokenEstimator, IndexConfig config) {
        this(tokenEstimator, config, null);
    }

    public CodeIndex(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null, null);
    }

    public void setSearchEngine(CodeSearchStrategy searchEngine) {
        this.searchEngine = searchEngine;
        if (cacheManager != null) {
            cacheManager.invalidateSearch(null);
        }
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

        if (cacheManager != null) {
            List<String> cached = cacheManager.getSearch("global", query);
            if (cached != null) {
                logger.debug("检索缓存命中: '{}' (耗时: {}ms)", query, System.currentTimeMillis() - startTime);
                return cached;
            }
        }

        List<SearchResult> results = searchEngine.search(query, maxResults);
        if (results.isEmpty()) {
            logger.debug("检索无结果: '{}'", query);
            return formattedResults;
        }

        int totalTokens = 0;
        int tokensPerResult = Math.max(500, maxTokens / Math.max(1, results.size()));

        for (SearchResult result : results) {
            StringBuilder item = new StringBuilder();
            item.append("📄 ").append(result.filePath).append("\n");
            item.append("   相关性: ").append(String.format("%.0f%%", result.score * 100)).append("\n");
            item.append("   摘要:\n");

            String preview = truncationService.truncateByExtension(result.preview, result.filePath, tokensPerResult);
            int resultTokens = tokenEstimator.estimateTextTokens(preview);

            if (totalTokens + resultTokens > maxTokens) {
                logger.debug("达到 token 上限 {}，截断结果", maxTokens);
                break;
            }

            item.append(preview).append("\n");
            formattedResults.add(item.toString());
            totalTokens += resultTokens;
        }

        if (cacheManager != null) {
            cacheManager.putSearch("global", query, formattedResults);
        }

        logger.debug("检索完成: '{}' 返回 {} 个结果，约 {} tokens (耗时: {}ms)",
                query, formattedResults.size(), totalTokens, System.currentTimeMillis() - startTime);

        return formattedResults;
    }

    public void cleanupCache() {
        if (cacheManager != null) {
            cacheManager.cleanup();
            logger.debug("检索缓存已清理");
        }
    }

    public void clearCache() {
        if (cacheManager != null) {
            cacheManager.invalidateSearch(null);
            logger.debug("清空所有检索缓存");
        }
    }

    public int getCacheSize() {
        return cacheManager != null ? cacheManager.size() : 0;
    }
}
