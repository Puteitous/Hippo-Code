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
import java.util.stream.Stream;

public class ReadFileTool implements ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReadFileTool.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_LINES_TO_READ = 2000;

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
                "内容已在上下文中，无需重复读取。\n" +
                "如需读取其他部分，请使用 offset/limit 参数。\n" +
                "示例: offset=%d, limit=%d 读取后续内容\n" +
                "</system-reminder>",
                filePath,
                timeDesc,
                accessCount,
                contentLength,
                MAX_LINES_TO_READ,
                MAX_LINES_TO_READ
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
                    "offset": {
                        "type": "integer",
                        "description": "起始行号（默认 0，从第 1 行开始）"
                    },
                    "limit": {
                        "type": "integer",
                        "description": "读取行数（默认 2000，最大 2000）"
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

            int offset = 0;
            if (arguments.has("offset") && !arguments.get("offset").isNull()) {
                offset = Math.max(0, arguments.get("offset").asInt());
            }

            int limit = MAX_LINES_TO_READ;
            if (arguments.has("limit") && !arguments.get("limit").isNull()) {
                limit = Math.min(MAX_LINES_TO_READ, Math.max(1, arguments.get("limit").asInt()));
            }

            FileCacheEntry cached = fileCache.get(filePath);
            if (cached != null && !cached.isFileModified(path) && offset == 0 && limit == MAX_LINES_TO_READ) {
                logger.debug("缓存命中: {} (访问次数: {})", filePath, cached.accessCount + 1);
                return cached.generateCacheHitMessage(filePath);
            }

            List<String> allLines = Files.readAllLines(path);
            int totalLines = allLines.size();

            if (totalLines > MAX_LINES_TO_READ && offset == 0 && !arguments.has("limit")) {
                throw new ToolExecutionException(
                    String.format("文件过大（%d 行），超过最大读取限制（%d 行）。\n" +
                    "请使用 offset 和 limit 参数分段读取。\n" +
                    "示例: offset=0, limit=%d 读取前 %d 行",
                    totalLines, MAX_LINES_TO_READ, MAX_LINES_TO_READ, MAX_LINES_TO_READ)
                );
            }

            int actualOffset = Math.min(offset, totalLines);
            int actualLimit = Math.min(limit, totalLines - actualOffset);
            
            String content;
            boolean isPartialRead = actualLimit < totalLines;

            if (isPartialRead) {
                int end = Math.min(actualOffset + actualLimit, totalLines);
                content = String.join("\n", allLines.subList(actualOffset, end));
            } else {
                content = String.join("\n", allLines);
            }

            long lastModified = Files.getLastModifiedTime(path).toMillis();
            if (offset == 0 && limit == MAX_LINES_TO_READ) {
                fileCache.put(filePath, new FileCacheEntry(content, lastModified, System.currentTimeMillis()));
            }
            
            String relativePath = PathSecurityUtils.getRelativePath(path);

            StringBuilder result = new StringBuilder();
            result.append("文件内容 (").append(relativePath);
            if (isPartialRead) {
                result.append(", 行 ").append(actualOffset + 1).append("-").append(actualOffset + actualLimit);
            }
            result.append("):\n");
            result.append("<file_content>\n");
            result.append(content);
            result.append("\n</file_content>\n");
            result.append("(").append(content.length()).append(" 字符");
            if (isPartialRead) {
                result.append(", 文件共 ").append(totalLines).append(" 行");
                result.append(", 已读取 ").append(actualOffset + 1).append("-").append(actualOffset + actualLimit).append(" 行");
                result.append("\n提示: 使用 offset/limit 参数继续读取其他部分");
            }
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
