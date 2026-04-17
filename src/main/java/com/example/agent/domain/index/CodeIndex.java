package com.example.agent.domain.index;

import com.example.agent.config.IndexConfig;
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
    private final TruncationService truncationService;

    public CodeIndex(TokenEstimator tokenEstimator, IndexConfig config) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("TokenEstimator cannot be null");
        }
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new IndexConfig();
        this.searchEngine = SearchEngineFactory.getDefault();
        this.truncationService = new TruncationService(tokenEstimator);
    }

    public CodeIndex(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    public void setSearchEngine(CodeSearchStrategy searchEngine) {
        this.searchEngine = searchEngine;
        if (searchEngine != null) {
            logger.info("检索引擎已切换为: {}", searchEngine.getClass().getSimpleName());
        }
    }

    public List<String> search(String query, int maxResults, int maxTokens) {
        List<String> formattedResults = new ArrayList<>();

        if (query == null) {
            return formattedResults;
        }
        if (maxResults <= 0 || maxTokens <= 0) {
            return formattedResults;
        }

        long startTime = System.currentTimeMillis();
        List<SearchResult> results = searchEngine.search(query, maxResults);
        int totalTokens = 0;

        if (!results.isEmpty()) {
            int tokensPerResult = Math.max(500, maxTokens / Math.max(1, results.size()));

            for (SearchResult result : results) {
                StringBuilder item = new StringBuilder();
                item.append("📄 ").append(result.filePath).append("\n");
                item.append("   相关性: ").append(String.format("%.0f%%", result.score * 100)).append("\n");
                item.append("   摘要:\n");

                String preview = truncationService.truncateByExtension(result.preview, result.filePath, tokensPerResult);
                int resultTokens = tokenEstimator.estimateTextTokens(preview);

                if (totalTokens + resultTokens > maxTokens) {
                    break;
                }

                item.append(preview).append("\n");
                formattedResults.add(item.toString());
                totalTokens += resultTokens;
            }
        }

        logger.debug("代码检索: '{}' 返回 {} 个结果 (耗时: {}ms)",
                query, formattedResults.size(), System.currentTimeMillis() - startTime);

        return formattedResults;
    }
}
