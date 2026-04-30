package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReadFileTool implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReadFileTool.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final Map<String, FileCacheEntry> fileCache = new ConcurrentHashMap<>();

    private static class FileCacheEntry {
        private final String content;
        private final long lastModified;
        private final long readTime;
        private final int contentLength;
        private volatile int accessCount;

        FileCacheEntry(String content, long lastModified, long readTime) {
            this.content = content;
            this.lastModified = lastModified;
            this.readTime = readTime;
            this.contentLength = content.length();
            this.accessCount = 1;
        }

        boolean isFileModified(Path path) {
            try {
                long currentModified = Files.getLastModifiedTime(path).toMillis();
                return currentModified != this.lastModified;
            } catch (IOException e) {
                logger.warn("检查文件修改时间失败: {}", path, e);
                return true;
            }
        }

        String generateCacheHitMessage(String filePath) {
            this.accessCount++;
            long timeSinceRead = System.currentTimeMillis() - readTime;
            String timeDesc;
            if (timeSinceRead < 1000) {
                timeDesc = "刚刚";
            } else if (timeSinceRead < 60000) {
                timeDesc = (timeSinceRead / 1000) + " 秒前";
            } else {
                timeDesc = (timeSinceRead / 60000) + " 分钟前";
            }

            return String.format(
                "<system-reminder>\n" +
                "文件 %s 内容未改变。\n" +
                "你已在 %s 读取过此文件（第 %d 次访问），文件大小 %d 字符。\n" +
                "请直接使用上下文中的内容，无需重复读取。\n" +
                "</system-reminder>",
                filePath,
                timeDesc,
                accessCount,
                contentLength
            );
        }
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取指定路径的文件内容。只能访问项目目录内的文件。";
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

            FileCacheEntry cached = fileCache.get(filePath);
            if (cached != null && !cached.isFileModified(path)) {
                logger.debug("缓存命中: {} (访问次数: {})", filePath, cached.accessCount + 1);
                return cached.generateCacheHitMessage(filePath);
            }

            String content = Files.readString(path);
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            fileCache.put(filePath, new FileCacheEntry(content, lastModified, System.currentTimeMillis()));
            
            String relativePath = PathSecurityUtils.getRelativePath(path);

            StringBuilder result = new StringBuilder();
            result.append("文件内容 (").append(relativePath);
            result.append("):\n");
            result.append("<file_content>\n");
            result.append(content);
            result.append("\n</file_content>\n");
            result.append("(").append(content.length()).append(" 字符");
            if (!content.endsWith("\n")) {
                result.append(", 文件末尾无换行符");
            }
            result.append(")\n");

            return result.toString();

        } catch (IOException e) {
            throw new ToolExecutionException("读取文件失败: " + e.getMessage(), e);
        }
    }

    public void invalidateCache(String filePath) {
        fileCache.remove(filePath);
        logger.debug("缓存失效: {}", filePath);
    }

    public void clearCache() {
        int size = fileCache.size();
        fileCache.clear();
        logger.info("清空文件缓存，共清除 {} 个条目", size);
    }

    public int getCacheSize() {
        return fileCache.size();
    }
}
