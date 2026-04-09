package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ColdMemory 代码库检索缓存层
 * 
 * 新架构：职责简化为检索引擎 + 缓存
 * - 不再预判，不再自动注入上下文
 * - 供 search_code 工具调用
 * - LLM 自主决定何时检索、检索什么
 * 
 */
public class ColdMemory {

    private static final Logger logger = LoggerFactory.getLogger(ColdMemory.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.ColdMemoryConfig config;
    private final CodeSearchEngine searchEngine;
    private final Map<String, CachedSearchResult> searchCache;

    public ColdMemory(TokenEstimator tokenEstimator, ContextConfig.ColdMemoryConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig.ColdMemoryConfig();
        this.searchEngine = new CodeSearchEngine();
        this.searchCache = new ConcurrentHashMap<>();
    }

    public ColdMemory(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    /**
     * 检索代码库
     *
     * @param query 检索查询
     * @param maxResults 最大结果数
     * @param maxTokens 最大 token 数
     * @return 格式化的检索结果列表
     */
    public List<String> search(String query, int maxResults, int maxTokens) {
        List<String> formattedResults = new ArrayList<>();

        // 参数边界校验
        if (query == null) {
            logger.debug("查询为 null，返回空结果");
            return formattedResults;
        }
        if (maxResults <= 0 || maxTokens <= 0) {
            logger.debug("参数无效 maxResults={}, maxTokens={}，返回空结果", maxResults, maxTokens);
            return formattedResults;
        }

        long startTime = System.currentTimeMillis();

        // 检查缓存
        CachedSearchResult cached = searchCache.get(query);
        if (cached != null && !cached.isExpired()) {
            logger.debug("检索缓存命中: '{}' (耗时: {}ms)", query, System.currentTimeMillis() - startTime);
            return cached.results;
        }

        // 执行检索
        List<SearchResult> results = searchEngine.search(query, maxResults);
        if (results.isEmpty()) {
            logger.debug("检索无结果: '{}'", query);
            return formattedResults;
        }

        // 格式化结果
        int totalTokens = 0;
        for (SearchResult result : results) {
            StringBuilder item = new StringBuilder();
            item.append("📄 ").append(result.filePath).append("\n");
            item.append("   相关性: ").append(String.format("%.0f%%", result.score * 100)).append("\n");
            item.append("   摘要:\n");

            String preview = result.preview;
            int resultTokens = tokenEstimator.estimateTextTokens(preview);

            if (totalTokens + resultTokens > maxTokens) {
                // 截断
                int ratio = (maxTokens - totalTokens) * 4;
                if (ratio > 0 && preview.length() > ratio) {
                    preview = preview.substring(0, ratio) + "\n   ... (已截断)";
                } else if (ratio <= 0) {
                    break;
                }
            }

            // 缩进显示
            String[] lines = preview.split("\n");
            for (String line : lines) {
                item.append("   ").append(line).append("\n");
            }

            formattedResults.add(item.toString());
            totalTokens += resultTokens;
        }

        // 缓存结果（5分钟过期）
        searchCache.put(query, new CachedSearchResult(formattedResults, 300 * 1000L));

        logger.debug("检索完成: '{}' 返回 {} 个结果 (耗时: {}ms)",
                query, formattedResults.size(), System.currentTimeMillis() - startTime);

        return formattedResults;
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return searchCache.size();
    }

    /**
     * 清理过期缓存
     */
    public void cleanupCache() {
        int before = searchCache.size();
        searchCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - searchCache.size();
        if (removed > 0) {
            logger.debug("清理了 {} 个过期的检索缓存", removed);
        }
    }

    /**
     * 清空所有缓存
     */
    public void clearCache() {
        searchCache.clear();
        logger.debug("清空所有检索缓存");
    }

    /**
     * 检索结果实体
     */
    private static class SearchResult {
        final String filePath;
        final String preview;
        final double score;

        SearchResult(String filePath, String preview, double score) {
            this.filePath = filePath;
            this.preview = preview;
            this.score = score;
        }
    }

    /**
     * 缓存的检索结果
     */
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

    /**
     * 代码检索引擎
     */
    private static class CodeSearchEngine {

        List<SearchResult> search(String query, int maxResults) {
            List<SearchResult> results = new ArrayList<>();

            logger.debug("执行检索: '{}'，最大结果数: {}", query, maxResults);

            // 简单的基于关键词的检索模拟
            // 实际项目可以集成语义向量数据库
            results.add(new SearchResult(
                    "src/main/java/com/example/agent/core/AgentContext.java",
                    "// AgentContext 核心类\npublic class AgentContext {\n    // 上下文管理核心\n}",
                    0.90
            ));

            results.add(new SearchResult(
                    "src/main/java/com/example/agent/execute/ConversationLoop.java",
                    "// 对话主循环\npublic class ConversationLoop {\n    // 意图识别、规划、执行流程\n}",
                    0.80
            ));

            results.add(new SearchResult(
                    "src/main/java/com/example/agent/tools/ReadFileTool.java",
                    "// 文件读取工具\npublic class ReadFileTool {\n    // 带缓存和智能截断\n}",
                    0.75
            ));

            // 限制结果数量
            if (results.size() > maxResults) {
                results = results.subList(0, maxResults);
            }

            return results;
        }
    }
}
