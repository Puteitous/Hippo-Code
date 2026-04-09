package com.example.agent.tools;

import com.example.agent.context.memory.WarmMemory;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * ReadFileTool 文件读取工具
 * 
 * 新设计：集成 WarmMemory 缓存和语言感知智能截断
 * - 自动缓存读取过的文件内容
 * - 根据文件类型自动应用智能截断（Java/Python/JS/TS）
 */
public class ReadFileTool implements ToolExecutor {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int DEFAULT_MAX_TOKENS = 4000;

    private WarmMemory warmMemory;

    public ReadFileTool() {
        this(null);
    }

    public ReadFileTool(WarmMemory warmMemory) {
        this.warmMemory = warmMemory;
    }

    public void setWarmMemory(WarmMemory warmMemory) {
        this.warmMemory = warmMemory;
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取指定路径的文件内容。支持缓存和智能截断。只能访问项目目录内的文件。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "要读取的文件路径（绝对路径或相对路径，只能访问项目目录内）"
                    },
                    "max_tokens": {
                        "type": "integer",
                        "description": "最大 token 数（可选，默认 4000），超过时会智能截断"
                    }
                },
                "required": ["path"]
            }
            """;
    }

    @Override
    public List<String> getAffectedPaths(JsonNode arguments) {
        if (arguments.has("path")) {
            return Collections.singletonList(arguments.get("path").asText());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean requiresFileLock() {
        return true;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("path") || arguments.get("path").isNull()) {
            throw new ToolExecutionException("缺少必需参数: path");
        }

        String filePath = arguments.get("path").asText();
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new ToolExecutionException("path 参数不能为空");
        }

        int maxTokens = arguments.has("max_tokens") && !arguments.get("max_tokens").isNull()
                ? arguments.get("max_tokens").asInt()
                : DEFAULT_MAX_TOKENS;

        Path path = PathSecurityUtils.validateAndResolve(filePath);

        if (!Files.exists(path)) {
            throw new ToolExecutionException("文件不存在: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new ToolExecutionException("不是常规文件: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new ToolExecutionException("文件不可读: " + filePath);
        }

        try {
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                throw new ToolExecutionException(
                    String.format("文件过大（%d 字节），最大支持 %d 字节（10MB）", fileSize, MAX_FILE_SIZE));
            }

            String content;
            boolean fromCache = false;

            // 使用 WarmMemory 读取（带缓存 + 智能截断）
            if (warmMemory != null) {
                content = warmMemory.readFile(filePath, maxTokens);
                fromCache = warmMemory.getCacheSize() > 0;
            } else {
                // 降级：直接读取文件
                content = Files.readString(path, StandardCharsets.UTF_8);
                // 简单截断
                if (content.length() > maxTokens * 4) {
                    content = content.substring(0, maxTokens * 4) + "\n\n... 文件内容过长，已截断 ...";
                }
            }

            if (content == null) {
                throw new ToolExecutionException("读取文件内容失败: " + filePath);
            }

            String relativePath = PathSecurityUtils.getRelativePath(path);

            StringBuilder result = new StringBuilder();
            result.append("文件内容 (").append(relativePath);
            if (fromCache) {
                result.append(" - 来自缓存");
            }
            result.append("):\n");
            result.append("────────────────────────────────────────\n");
            result.append(content);
            if (!content.endsWith("\n")) {
                result.append("\n");
            }
            result.append("────────────────────────────────────────\n");
            result.append("(").append(content.length()).append(" 字符");
            if (warmMemory != null) {
                result.append(", 智能截断已启用");
            }
            result.append(")\n");

            return result.toString();

        } catch (IOException e) {
            throw new ToolExecutionException("读取文件失败: " + e.getMessage(), e);
        }
    }
}
