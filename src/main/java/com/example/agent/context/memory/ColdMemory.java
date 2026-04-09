package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ColdMemory 管理类
 * 负责代码库检索，基于语义和关键词的文件搜索
 */
public class ColdMemory {

    private static final Logger logger = LoggerFactory.getLogger(ColdMemory.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.ColdMemoryConfig config;
    private final CodeSearchEngine searchEngine;
    private final Map<String, List<SearchResult>> searchCache;

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
     * 基于用户输入检索相关文件
     *
     * @param userInput 用户输入
     * @param maxTokens 最大 token 限制
     * @return 包含检索结果的消息列表
     */
    public List<Message> retrieve(String userInput, int maxTokens) {
        List<Message> messages = new ArrayList<>();

        if (!config.isEnabled() || userInput == null || userInput.isEmpty()) {
            return messages;
        }

        // 检查缓存
        List<SearchResult> cachedResults = searchCache.get(userInput);
        if (cachedResults != null) {
            logger.debug("从缓存获取检索结果");
            return convertResultsToMessages(cachedResults, maxTokens);
        }

        // 执行检索
        List<SearchResult> results = searchEngine.search(userInput, config.getMaxResults());
        if (results.isEmpty()) {
            logger.debug("没有找到相关文件");
            return messages;
        }

        // 缓存结果
        searchCache.put(userInput, results);

        // 转换为消息
        messages = convertResultsToMessages(results, maxTokens);

        return messages;
    }

    /**
     * 将检索结果转换为消息
     */
    private List<Message> convertResultsToMessages(List<SearchResult> results, int maxTokens) {
        List<Message> messages = new ArrayList<>();
        int totalTokens = 0;

        for (SearchResult result : results) {
            String content = result.getContent();
            int tokens = tokenEstimator.estimateTextTokens(content);

            if (totalTokens + tokens > maxTokens) {
                logger.debug("达到 token 限制，停止添加检索结果");
                break;
            }

            // 创建工具消息
            Message message = Message.toolResult(
                    "cold_memory_" + result.getFilePath(),
                    "search_file",
                    "文件: " + result.getFilePath() + "\n" +
                            "相关性: " + result.getRelevanceScore() + "\n\n" +
                            content
            );
            
            messages.add(message);
            totalTokens += tokens;
        }

        return messages;
    }

    /**
     * 清理缓存
     */
    public void cleanupCache() {
        searchCache.clear();
        logger.debug("清除检索缓存");
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return searchCache.size();
    }

    /**
     * 检索结果类
     */
    public static class SearchResult {
        private final String filePath;
        private final String content;
        private final double relevanceScore;

        public SearchResult(String filePath, String content, double relevanceScore) {
            this.filePath = filePath;
            this.content = content;
            this.relevanceScore = relevanceScore;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getContent() {
            return content;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }
    }

    /**
     * 代码搜索引擎（内部实现）
     */
    private static class CodeSearchEngine {

        /**
         * 搜索相关文件
         *
         * @param query 搜索查询
         * @param maxResults 最大结果数
         * @return 搜索结果列表
         */
        public List<SearchResult> search(String query, int maxResults) {
            List<SearchResult> results = new ArrayList<>();

            // TODO: 实现真实的代码检索逻辑
            // 1. 扫描项目目录
            // 2. 提取文件内容
            // 3. 计算相关性得分
            // 4. 排序并返回结果

            logger.debug("搜索查询: '{}'，最大结果数: {}", query, maxResults);

            // 模拟搜索结果
            results.add(new SearchResult(
                    "src/main/java/com/example/agent/core/AgentContext.java",
                    "// AgentContext 核心类\npublic class AgentContext {\n    // 核心方法和属性\n}",
                    0.95
            ));

            results.add(new SearchResult(
                    "src/main/java/com/example/agent/context/policy/ThreeTierPolicy.java",
                    "// 三层记忆策略\npublic class ThreeTierPolicy implements ContextPolicy {\n    // 策略实现\n}",
                    0.85
            ));

            results.add(new SearchResult(
                    "src/main/java/com/example/agent/context/memory/WarmMemory.java",
                    "// WarmMemory 管理类\npublic class WarmMemory {\n    // @ 引用处理\n}",
                    0.80
            ));

            // 限制结果数量
            if (results.size() > maxResults) {
                results = results.subList(0, maxResults);
            }

            return results;
        }
    }
}
