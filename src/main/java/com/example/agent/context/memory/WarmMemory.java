package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.language.LanguageProvider;
import com.example.agent.context.language.LanguageProviderFactory;
import com.example.agent.context.parser.AtReferenceParser;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WarmMemory 管理类
 * 负责处理 @ 引用解析和文件内容缓存
 */
public class WarmMemory {

    private static final Logger logger = LoggerFactory.getLogger(WarmMemory.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.WarmMemoryConfig config;
    private final Map<String, CachedFile> fileCache;
    private final AtReferenceParser referenceParser;
    private final LanguageProviderFactory languageProviderFactory;

    public WarmMemory(TokenEstimator tokenEstimator, ContextConfig.WarmMemoryConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig.WarmMemoryConfig();
        this.fileCache = new HashMap<>();
        this.referenceParser = new AtReferenceParser();
        this.languageProviderFactory = new LanguageProviderFactory();
    }

    public WarmMemory(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    /**
     * 处理 @ 引用，返回增强的消息列表
     *
     * @param userInput 用户输入
     * @return 包含引用文件内容的消息列表
     */
    public List<Message> processReferences(String userInput) {
        List<Message> messages = new ArrayList<>();

        if (!config.isAtReferenceEnabled() || userInput == null || userInput.isEmpty()) {
            return messages;
        }

        // 解析 @ 引用
        List<String> references = parseAtReferences(userInput);
        if (references.isEmpty()) {
            return messages;
        }

        // 限制引用数量
        if (references.size() > config.getMaxRefsPerMessage()) {
            logger.warn("引用文件数量超过限制 ({} > {})，只处理前 {} 个",
                    references.size(), config.getMaxRefsPerMessage(), config.getMaxRefsPerMessage());
            references = references.subList(0, config.getMaxRefsPerMessage());
        }

        // 处理每个引用
        for (String path : references) {
            try {
                String content = loadFileContent(path);
                if (content != null && !content.isEmpty()) {
                    // 创建工具消息，模拟文件内容
                    Message message = Message.toolResult(
                            "warm_memory_" + path,
                            "read_file",
                            "文件: " + path + "\n\n" + content
                    );
                    messages.add(message);
                }
            } catch (Exception e) {
                logger.warn("加载文件失败: {} - {}", path, e.getMessage());
            }
        }

        return messages;
    }

    /**
     * 解析用户输入中的 @ 引用
     */
    private List<String> parseAtReferences(String userInput) {
        return referenceParser.parse(userInput);
    }

    /**
     * 加载文件内容（支持缓存和智能截断）
     */
    private String loadFileContent(String path) throws IOException {
        // 检查缓存
        CachedFile cachedFile = fileCache.get(path);
        if (cachedFile != null && !cachedFile.isExpired()) {
            logger.debug("从缓存加载文件: {}", path);
            return cachedFile.getContent();
        }

        // 解析路径
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            logger.warn("文件不存在: {}", path);
            return null;
        }

        // 读取文件内容
        String content = Files.readString(filePath);
        int tokens = tokenEstimator.estimateTextTokens(content);

        // 检查 token 限制，如果需要则进行智能截断
        if (tokens > config.getMaxFileTokens()) {
            logger.warn("文件 {} 超过 token 限制 ({} > {})，需要截断",
                    path, tokens, config.getMaxFileTokens());
            
            // 尝试使用 LanguageProvider 进行智能截断
            LanguageProvider provider = languageProviderFactory.getProvider(path);
            if (provider != null) {
                logger.info("使用 {} 进行智能截断", provider.getLanguageName());
                content = provider.truncate(content, config.getMaxFileTokens());
                tokens = tokenEstimator.estimateTextTokens(content);
                logger.info("截断后: {} tokens", tokens);
            } else {
                logger.warn("未找到对应的 LanguageProvider，使用简单截断");
                content = simpleTruncate(content, config.getMaxFileTokens());
            }
        }

        // 缓存文件内容
        fileCache.put(path, new CachedFile(content, config.getCacheTtlSeconds()));
        logger.info("加载文件: {}, 大小: {} 字符, {} tokens", path, content.length(), tokens);

        return content;
    }

    /**
     * 简单截断（当没有对应的 LanguageProvider 时使用）
     */
    private String simpleTruncate(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int currentTokens = tokenEstimator.estimateTextTokens(content);
        if (currentTokens <= maxTokens) {
            return content;
        }

        // 简单截断：保留前 70%，后 30%
        String[] lines = content.split("\n");
        int keepLines = (int) (lines.length * 0.7);
        
        StringBuilder result = new StringBuilder();
        
        // 前 70%
        for (int i = 0; i < keepLines && i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }
        
        result.append("\n// ... 内容省略 ...\n\n");
        
        // 后 30%
        int startIndex = Math.max(keepLines, lines.length - (lines.length - keepLines));
        for (int i = startIndex; i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }
        
        result.append("\n// [文件已截断]");
        
        return result.toString();
    }

    /**
     * 清理过期的缓存
     */
    public void cleanupCache() {
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, CachedFile> entry : fileCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredKeys.add(entry.getKey());
            }
        }

        for (String key : expiredKeys) {
            fileCache.remove(key);
            logger.debug("清理过期缓存: {}", key);
        }
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        fileCache.clear();
        logger.debug("清除所有文件缓存");
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return fileCache.size();
    }

    /**
     * 缓存文件类
     */
    private static class CachedFile {
        private final String content;
        private final long timestamp;
        private final int ttlSeconds;

        public CachedFile(String content, int ttlSeconds) {
            this.content = content;
            this.timestamp = System.currentTimeMillis();
            this.ttlSeconds = ttlSeconds;
        }

        public String getContent() {
            return content;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttlSeconds * 1000L;
        }
    }
}
