package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.language.LanguageProvider;
import com.example.agent.context.language.LanguageProviderFactory;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * WarmMemory 文件缓存层
 * 提供文件内容缓存和语言感知的智能截断
 * 
 * 新设计：只负责缓存 + 智能截断，不自动注入上下文
 * 由 LLM 自主决定何时调用 read_file 工具
 */
public class WarmMemory {

    private static final Logger logger = LoggerFactory.getLogger(WarmMemory.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.WarmMemoryConfig config;
    private final Map<String, CachedFile> fileCache;
    private final LanguageProviderFactory languageProviderFactory;

    public WarmMemory(TokenEstimator tokenEstimator, ContextConfig.WarmMemoryConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig.WarmMemoryConfig();
        this.fileCache = new HashMap<>();
        this.languageProviderFactory = new LanguageProviderFactory();
    }

    public WarmMemory(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    /**
     * 读取文件内容（带缓存）
     *
     * @param filePath 文件路径
     * @return 文件内容
     */
    public String readFile(String filePath) {
        return readFile(filePath, config.getMaxFileTokens());
    }

    /**
     * 读取文件内容（带缓存 + 智能截断）
     *
     * @param filePath 文件路径
     * @param maxTokens 最大 token 数
     * @return 文件内容
     */
    public String readFile(String filePath, int maxTokens) {
        long startTime = System.currentTimeMillis();

        // 参数边界校验
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("文件路径为空或 null");
            return null;
        }
        // token <= 0 时使用配置的默认值
        int effectiveMaxTokens = maxTokens > 0 ? maxTokens : config.getMaxFileTokens();

        // 检查缓存
        CachedFile cached = fileCache.get(filePath);
        if (cached != null && !cached.isExpired()) {
            logger.debug("缓存命中: {} (耗时: {}ms)", filePath, System.currentTimeMillis() - startTime);
            return applySmartTruncation(filePath, cached.content, effectiveMaxTokens);
        }

        // 读取文件
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.warn("文件不存在: {}", filePath);
                return null;
            }

            String content = Files.readString(path);

            // 存入缓存
            fileCache.put(filePath, new CachedFile(content, config.getCacheTtlSeconds() * 1000L));

            logger.debug("读取文件: {} ({} 字符, 耗时: {}ms)",
                    filePath, content.length(), System.currentTimeMillis() - startTime);

            // 应用智能截断
            return applySmartTruncation(filePath, content, effectiveMaxTokens);

        } catch (IOException e) {
            logger.error("读取文件失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * 应用语言感知的智能截断
     */
    private String applySmartTruncation(String filePath, String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        int tokenCount = tokenEstimator.estimateTextTokens(content);
        if (tokenCount <= maxTokens) {
            return content;
        }

        // 需要截断 - 使用对应语言的处理器
        LanguageProvider provider = languageProviderFactory.getProvider(filePath);
        if (provider != null) {
            logger.debug("对 {} 使用 {} 进行智能截断", filePath, provider.getLanguageName());
            return provider.truncate(content, maxTokens);
        }

        // 无对应语言处理器，使用简单尾部截断
        logger.debug("对 {} 使用简单截断 ({} tokens -> {} tokens)", filePath, tokenCount, maxTokens);
        return simpleTruncate(content, maxTokens);
    }

    /**
     * 简单截断（保留头部）
     */
    private String simpleTruncate(String content, int maxTokens) {
        StringBuilder result = new StringBuilder();
        int currentTokens = 0;
        String[] lines = content.split("\n");
        boolean truncated = false;

        for (String line : lines) {
            int lineTokens = tokenEstimator.estimateTextTokens(line + "\n");
            if (currentTokens + lineTokens > maxTokens) {
                truncated = true;
                break;
            }
            result.append(line).append("\n");
            currentTokens += lineTokens;
        }

        if (truncated) {
            result.append("\n// ... 文件内容过长，已截断 ...\n");
        }

        return result.toString();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return fileCache.size();
    }

    /**
     * 清理过期缓存
     */
    public void cleanupCache() {
        int before = fileCache.size();
        fileCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - fileCache.size();
        if (removed > 0) {
            logger.debug("清理了 {} 个过期缓存项", removed);
        }
    }

    /**
     * 清空所有缓存
     */
    public void clearCache() {
        fileCache.clear();
        logger.debug("清空所有文件缓存");
    }

    /**
     * 缓存文件实体
     */
    private static class CachedFile {
        final String content;
        final long expireTime;

        CachedFile(String content, long ttlMillis) {
            this.content = content;
            this.expireTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
