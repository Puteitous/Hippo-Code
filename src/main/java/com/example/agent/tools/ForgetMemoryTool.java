package com.example.agent.tools;

import com.example.agent.memory.MemoryStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆删除工具
 * 
 * 根据用户要求删除指定的记忆条目
 * 支持按 ID 删除或按关键词搜索后删除
 */
public class ForgetMemoryTool implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ForgetMemoryTool.class);

    private final MemoryStore memoryStore;

    public ForgetMemoryTool(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public String getName() {
        return "forget_memory";
    }

    @Override
    public String getDescription() {
        return """
            forget_memory: 根据用户要求删除指定的记忆条目。
            可以通过记忆 ID 直接删除，或通过关键词搜索相关记忆后删除。
            删除操作不可恢复，请谨慎使用。""";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "id": {
                        "type": "string",
                        "description": "The ID of the memory to delete (optional if using query)"
                    },
                    "query": {
                        "type": "string",
                        "description": "Search query to find memories to delete (optional if using id)"
                    },
                    "reason": {
                        "type": "string",
                        "description": "Reason for deleting this memory (for logging purposes)"
                    }
                }
            }""";
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        String id = arguments.has("id") ? arguments.get("id").asText(null) : null;
        String query = arguments.has("query") ? arguments.get("query").asText(null) : null;
        String reason = arguments.has("reason") ? arguments.get("reason").asText("用户要求删除") : "用户要求删除";

        if (id == null && query == null) {
            throw new ToolExecutionException("必须提供 id 或 query 参数之一");
        }

        StringBuilder result = new StringBuilder();

        if (id != null) {
            // 按 ID 删除
            var entry = memoryStore.findById(id);
            if (entry == null) {
                result.append("记忆不存在：").append(id);
            } else {
                memoryStore.delete(id);
                result.append("已删除记忆：").append(id).append("\n");
                result.append("标题：").append(extractTitle(entry.getContent())).append("\n");
                result.append("原因：").append(reason);
                logger.info("记忆删除：id={}, reason={}", id, reason);
            }
        }

        if (query != null) {
            // 按关键词搜索后删除
            var entries = memoryStore.search(query);
            if (entries.isEmpty()) {
                result.append("未找到与 '").append(query).append("' 相关的记忆");
            } else {
                result.append("找到 ").append(entries.size()).append(" 条相关记忆，已删除：\n\n");
                for (var entry : entries) {
                    memoryStore.delete(entry.getId());
                    result.append("- ").append(extractTitle(entry.getContent()))
                          .append(" (ID: ").append(entry.getId()).append(")\n");
                    logger.info("记忆删除（搜索）：id={}, query={}, reason={}", entry.getId(), query, reason);
                }
            }
        }

        return result.toString();
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
