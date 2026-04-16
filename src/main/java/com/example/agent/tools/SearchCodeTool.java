package com.example.agent.tools;

import com.example.agent.domain.index.CodeIndex;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;

public class SearchCodeTool implements ToolExecutor {

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final int DEFAULT_MAX_TOKENS = 5000;

    private final CodeIndex codeIndex;

    public SearchCodeTool() {
        this(null);
    }

    public SearchCodeTool(CodeIndex codeIndex) {
        this.codeIndex = codeIndex;
    }

    @Override
    public String getName() {
        return "search_code";
    }

    @Override
    public String getDescription() {
        return "语义检索代码库，查找与查询相关的代码文件。" +
               "基于相关性返回最匹配的文件摘要。当你需要了解项目结构、" +
               "查找相关代码、或回答需要上下文的问题时使用此工具。" +
               "只能检索项目目录内的代码文件。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "检索查询，描述你想查找的代码或概念"
                    },
                    "max_results": {
                        "type": "integer",
                        "description": "最多返回多少个结果（可选，默认 3）"
                    },
                    "max_tokens": {
                        "type": "integer",
                        "description": "检索结果的最大 token 数（可选，默认 5000）"
                    }
                },
                "required": ["query"]
            }
            """;
    }

    @Override
    public List<String> getAffectedPaths(JsonNode arguments) {
        return Collections.emptyList();
    }

    @Override
    public boolean requiresFileLock() {
        return false;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("query") || arguments.get("query").isNull()) {
            throw new ToolExecutionException("缺少必需参数: query");
        }

        String query = arguments.get("query").asText();
        if (query == null || query.trim().isEmpty()) {
            throw new ToolExecutionException("query 参数不能为空");
        }

        int maxResults = arguments.has("max_results") && !arguments.get("max_results").isNull()
                ? arguments.get("max_results").asInt()
                : DEFAULT_MAX_RESULTS;

        int maxTokens = arguments.has("max_tokens") && !arguments.get("max_tokens").isNull()
                ? arguments.get("max_tokens").asInt()
                : DEFAULT_MAX_TOKENS;

        StringBuilder result = new StringBuilder();
        result.append("SEARCH_CODE: '").append(query).append("'\n");

        if (codeIndex != null) {
            List<String> results = codeIndex.search(query, maxResults, maxTokens);

            if (results.isEmpty()) {
                result.append("EMPTY\n");
            } else {
                for (int i = 0; i < results.size(); i++) {
                    result.append(results.get(i)).append("\n");
                }
            }
        } else {
            result.append("INDEX_NOT_AVAILABLE\n");
        }

        return result.toString();
    }
}
