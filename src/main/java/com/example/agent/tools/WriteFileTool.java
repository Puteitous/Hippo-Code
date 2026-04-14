package com.example.agent.tools;

import com.example.agent.domain.cache.CacheManager;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

public class WriteFileTool implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WriteFileTool.class);
    private static final long MAX_CONTENT_SIZE = 10 * 1024 * 1024;

    private final CacheManager cacheManager;

    public WriteFileTool() {
        this(null);
    }

    public WriteFileTool(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "将内容写入指定路径的文件。如果文件不存在则创建，如果存在则覆盖。只能访问项目目录内的文件。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "要写入的文件路径（绝对路径或相对路径，只能访问项目目录内）"
                    },
                    "content": {
                        "type": "string",
                        "description": "要写入文件的内容"
                    }
                },
                "required": ["path", "content"]
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
        if (!arguments.has("content") || arguments.get("content").isNull()) {
            throw new ToolExecutionException("缺少必需参数: content");
        }

        String filePath = arguments.get("path").asText();
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new ToolExecutionException("path 参数不能为空");
        }
        
        String content = arguments.get("content").asText();
        if (content == null) {
            content = "";
        }
        
        if (content.length() > MAX_CONTENT_SIZE) {
            throw new ToolExecutionException(
                String.format("内容过大（%d 字符），最大支持 %d 字符（10MB）", 
                    content.length(), MAX_CONTENT_SIZE));
        }

        Path path = PathSecurityUtils.validateAndResolve(filePath);

        try {
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            boolean fileExisted = Files.exists(path);
            
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            String absolutePath = path.toAbsolutePath() != null ? path.toAbsolutePath().toString() : path.toString();
            String relativePath = PathSecurityUtils.getRelativePath(path);
            String action = fileExisted ? "覆盖" : "创建";

            if (cacheManager != null) {
                cacheManager.onFileChanged(absolutePath);
                logger.debug("写入文件后触发缓存失效: {}", absolutePath);
            }

            return String.format("文件%s成功: %s (%d 字符)", action, relativePath, content.length());
        } catch (IOException e) {
            throw new ToolExecutionException("写入文件失败: " + e.getMessage(), e);
        }
    }
}
