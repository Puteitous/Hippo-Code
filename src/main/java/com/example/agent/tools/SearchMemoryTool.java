package com.example.agent.tools;

import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.FrontmatterParser;
import com.example.agent.memory.embedding.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 记忆语义检索工具
 * 
 * LLM 通过此工具主动检索长期记忆
 * 使用自然语言查询，返回匹配记忆的摘要（不返回完整内容）
 */
public class SearchMemoryTool implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SearchMemoryTool.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_MIN_SCORE = 0.6;

    private final MemoryStore memoryStore;
    private final EmbeddingService embeddingService;

    public SearchMemoryTool(MemoryStore memoryStore, EmbeddingService embeddingService) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
    }

    @Override
    public String getName() {
        return "search_memory";
    }

    @Override
    public String getDescription() {
        return """
            search_memory: 使用自然语言检索长期记忆。
            适用场景：(1) 用户提及过去的工作、决策或偏好，但不在当前上下文中。
            (2) 需要验证项目特定的约束条件。
            查询应为你想要查找内容的自然语言描述，
            例如："我们决定的数据库连接池配置"。
            返回匹配记忆的 ID、标题和摘要。
            如需获取完整内容，请使用 recall_memory 并传入返回的 ID。""";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Natural language description of what you're looking for in long-term memory"
                    },
                    "top_k": {
                        "type": "integer",
                        "description": "Maximum number of results to return (default: 5)",
                        "default": 5
                    }
                },
                "required": ["query"]
            }""";
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("query") || arguments.get("query").asText().isBlank()) {
            throw new ToolExecutionException("query 参数不能为空");
        }

        if (embeddingService == null || !embeddingService.isAvailable()) {
            return "Error: Vector search is not available. Memory vectorization service is not initialized.";
        }

        String query = arguments.get("query").asText();
        int topK = arguments.has("top_k") ? arguments.get("top_k").asInt() : DEFAULT_TOP_K;
        topK = Math.min(Math.max(topK, 1), 20);

        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new ToolExecutionException("工具执行被中断");
            }

            float[] queryEmbedding = embeddingService.embed(query);

            if (Thread.currentThread().isInterrupted()) {
                throw new ToolExecutionException("工具执行被中断");
            }

            List<MemoryEntry> results = memoryStore.searchSimilar(queryEmbedding, topK, DEFAULT_MIN_SCORE);

            if (results.isEmpty()) {
                logger.info("记忆检索：query=\"{}\", 未找到相关记忆", query);
                return String.format("未找到与 \"%s\" 相关的记忆。建议：(1) 尝试使用不同的关键词 (2) 使用 recall_memory 工具时请提供具体的记忆 ID", query);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d relevant memories for query: \"%s\"\n\n", results.size(), query));

            for (int i = 0; i < results.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new ToolExecutionException("工具执行被中断");
                }

                MemoryEntry entry = results.get(i);
                String title = extractTitle(entry.getContent());
                String summary = FrontmatterParser.generateHook(entry.getContent());
                
                sb.append(String.format("%d. [%s] %s (ID: %s)\n", 
                    i + 1, entry.getType().getDisplayName(), title, entry.getId()));
                sb.append(String.format("   Summary: %s\n", summary));
                sb.append(String.format("   Use recall_memory with ID \"%s\" to get full content\n\n", entry.getId()));
            }

            logger.info("记忆检索：query=\"{}\", 找到 {} 条结果", query, results.size());
            return sb.toString();

        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("记忆检索失败", e);
            return "Error during memory search: " + e.getMessage();
        }
    }

    private String extractTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "Untitled";
        }
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return lines[0].trim();
    }
}
