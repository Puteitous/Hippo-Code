package com.example.agent.tools;

import com.example.agent.service.FileContentService;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class ReadFileTool implements ToolExecutor {

    private static final int DEFAULT_MAX_TOKENS = 4000;

    private FileContentService fileContentService;

    public ReadFileTool() {
        this(null);
    }

    public ReadFileTool(FileContentService fileContentService) {
        this.fileContentService = fileContentService;
    }

    public void setFileContentService(FileContentService fileContentService) {
        this.fileContentService = fileContentService;
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
            String content;
            if (fileContentService != null) {
                content = fileContentService.readFile(filePath, maxTokens);
            } else {
                content = Files.readString(path);
            }

            if (content == null) {
                throw new ToolExecutionException("读取文件内容失败: " + filePath);
            }

            String relativePath = PathSecurityUtils.getRelativePath(path);

            StringBuilder result = new StringBuilder();
            result.append("文件内容 (").append(relativePath);
            result.append("):\n");
            result.append("────────────────────────────────────────\n");
            result.append(content);
            if (!content.endsWith("\n")) {
                result.append("\n");
            }
            result.append("────────────────────────────────────────\n");
            result.append("(").append(content.length()).append(" 字符");
            if (fileContentService != null) {
                result.append(", 智能截断已启用");
            }
            result.append(")\n");

            return result.toString();

        } catch (IOException e) {
            throw new ToolExecutionException("读取文件失败: " + e.getMessage(), e);
        }
    }
}
