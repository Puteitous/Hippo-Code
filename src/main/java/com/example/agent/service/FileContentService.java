package com.example.agent.service;

import com.example.agent.domain.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileContentService {

    private static final Logger logger = LoggerFactory.getLogger(FileContentService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final CacheManager cacheManager;

    public FileContentService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public String readFile(String filePath) {
        return readFile(filePath, -1);
    }

    public String readFile(String filePath, int maxTokens) {
        long startTime = System.currentTimeMillis();

        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("文件路径为空或 null");
            return null;
        }

        String cacheKey = "file:" + filePath;

        String cached = cacheManager.get(cacheKey);
        if (cached != null) {
            logger.debug("缓存命中: {} (耗时: {}ms)", filePath, System.currentTimeMillis() - startTime);
            return cached;
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.warn("文件不存在: {}", filePath);
                return null;
            }

            if (Files.size(path) > MAX_FILE_SIZE) {
                logger.warn("文件过大: {} ({} bytes)", filePath, Files.size(path));
                return "// 错误：文件超过 10MB 限制";
            }

            String content = Files.readString(path);
            cacheManager.put(cacheKey, content, 30 * 60);

            logger.debug("读取文件: {} ({} 字符, 耗时: {}ms)",
                    filePath, content.length(), System.currentTimeMillis() - startTime);

            return content;

        } catch (IOException e) {
            logger.error("读取文件失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    public String readFileLines(String filePath, int startLine, int endLine) {
        String content = readFile(filePath, Integer.MAX_VALUE);
        if (content == null) {
            return null;
        }

        String[] lines = content.split("\n");
        int start = Math.max(0, startLine - 1);
        int end = Math.min(lines.length, endLine);

        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            result.append(lines[i]).append("\n");
        }

        logger.debug("读取文件 {} 的行 {}-{}", filePath, startLine, endLine);
        return result.toString();
    }

    public void invalidateCache(String filePath) {
        cacheManager.invalidate("file:" + filePath);
        logger.debug("文件缓存已失效: {}", filePath);
    }

    public int getCacheSize() {
        return cacheManager.size();
    }

}
