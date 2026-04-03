package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobTool implements ToolExecutor {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DEPTH = 20;

    @Override
    public String getName() {
        return "glob";
    }

    @Override
    public String getDescription() {
        return "使用 glob 模式查找文件。支持通配符：* 匹配任意字符（不包括目录分隔符），** 匹配任意层级的目录。" +
               "例如：'**/*.java' 查找所有 Java 文件，'src/**/*.txt' 查找 src 目录下的所有 txt 文件。" +
               "返回匹配的文件列表，按修改时间排序（最新的在前）。只能搜索项目目录内的文件。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "pattern": {
                        "type": "string",
                        "description": "glob 模式，例如 '**/*.java' 或 'src/**/*.txt'"
                    },
                    "path": {
                        "type": "string",
                        "description": "搜索的起始目录（默认为项目根目录）"
                    },
                    "max_results": {
                        "type": "integer",
                        "description": "最大返回结果数（默认 100，最大 1000）",
                        "default": 100,
                        "minimum": 1,
                        "maximum": 1000
                    }
                },
                "required": ["pattern"]
            }
            """;
    }

    @Override
    public List<String> getAffectedPaths(JsonNode arguments) {
        if (arguments.has("path")) {
            return Collections.singletonList(arguments.get("path").asText());
        }
        return Collections.singletonList(".");
    }

    @Override
    public boolean requiresFileLock() {
        return false;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("pattern")) {
            throw new ToolExecutionException("缺少必需参数: pattern");
        }

        String pattern = arguments.get("pattern").asText();
        String searchPath = arguments.has("path") ? arguments.get("path").asText() : ".";
        int maxResults = arguments.has("max_results") ? arguments.get("max_results").asInt() : 100;
        
        maxResults = Math.max(1, Math.min(MAX_RESULTS, maxResults));

        Path basePath = PathSecurityUtils.validateAndResolve(searchPath);

        if (!Files.exists(basePath)) {
            throw new ToolExecutionException("搜索路径不存在: " + searchPath);
        }

        if (!Files.isDirectory(basePath)) {
            throw new ToolExecutionException("搜索路径不是目录: " + searchPath);
        }

        if (!Files.isReadable(basePath)) {
            throw new ToolExecutionException("搜索路径不可读: " + searchPath);
        }

        try {
            String normalizedPattern = normalizePattern(pattern);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
            
            List<FileInfo> results;
            try (Stream<Path> stream = Files.walk(basePath, MAX_DEPTH)) {
                results = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isWithinProject(p))
                    .filter(p -> matcher.matches(basePath.relativize(p)))
                    .limit(maxResults * 2L)
                    .map(this::toFileInfo)
                    .sorted((a, b) -> b.modifiedTime.compareTo(a.modifiedTime))
                    .limit(maxResults)
                    .collect(Collectors.toList());
            }

            return formatResults(results, pattern, searchPath, maxResults);
            
        } catch (IOException e) {
            throw new ToolExecutionException("搜索文件失败: " + e.getMessage(), e);
        }
    }

    private String normalizePattern(String pattern) {
        if (!pattern.startsWith("**") && !pattern.startsWith("/") && !pattern.contains("/")) {
            return "**/" + pattern;
        }
        return pattern;
    }

    private boolean isWithinProject(Path path) {
        try {
            PathSecurityUtils.validateAndResolve(path.toString());
            return true;
        } catch (ToolExecutionException e) {
            return false;
        }
    }

    private FileInfo toFileInfo(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long size = attrs.size();
            FileTime modifiedTime = attrs.lastModifiedTime();
            String relativePath = PathSecurityUtils.getRelativePath(path);
            return new FileInfo(relativePath, size, modifiedTime);
        } catch (IOException e) {
            return new FileInfo(PathSecurityUtils.getRelativePath(path), 0, FileTime.fromMillis(0));
        }
    }

    private String formatResults(List<FileInfo> results, String pattern, String searchPath, int maxResults) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Glob 搜索结果\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        sb.append("模式: ").append(pattern).append("\n");
        sb.append("路径: ").append(searchPath).append("\n");
        sb.append("─────────────────────────────────────────────────────────────\n");

        if (results.isEmpty()) {
            sb.append("未找到匹配的文件\n");
        } else {
            for (FileInfo info : results) {
                sb.append("📄 ").append(String.format("%-50s", info.relativePath));
                sb.append("  ").append(String.format("%8s", formatSize(info.size)));
                sb.append("  ").append(formatTime(info.modifiedTime)).append("\n");
            }

            sb.append("─────────────────────────────────────────────────────────────\n");
            sb.append(String.format("找到 %d 个文件", results.size()));
            
            if (results.size() >= maxResults) {
                sb.append("（已达到最大结果数限制）");
            }
            sb.append("\n");

            long totalSize = results.stream().mapToLong(f -> f.size).sum();
            sb.append(String.format("总大小: %s\n", formatSize(totalSize)));
        }

        return sb.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private String formatTime(FileTime fileTime) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            fileTime.toInstant(), ZoneId.systemDefault()
        );
        return dateTime.format(TIME_FORMATTER);
    }

    private static class FileInfo {
        final String relativePath;
        final long size;
        final FileTime modifiedTime;

        FileInfo(String relativePath, long size, FileTime modifiedTime) {
            this.relativePath = relativePath;
            this.size = size;
            this.modifiedTime = modifiedTime;
        }
    }
}
