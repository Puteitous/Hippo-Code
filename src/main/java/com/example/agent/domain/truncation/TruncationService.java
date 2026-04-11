package com.example.agent.domain.truncation;

import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TruncationService {

    private static final Logger logger = LoggerFactory.getLogger(TruncationService.class);
    public static final int GLOBAL_HARD_LIMIT = 4096;

    private final TokenEstimator tokenEstimator;
    private final Map<ContentType, TruncationStrategy> strategies;
    private final TruncationStrategy defaultStrategy;

    public TruncationService(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
        this.strategies = new HashMap<>();
        this.defaultStrategy = new CodeTruncation(tokenEstimator);
        registerStrategy(ContentType.CODE, new CodeTruncation(tokenEstimator));
        registerStrategy(ContentType.PLAIN_TEXT, new HeadTailTruncation(tokenEstimator));
    }

    public void registerStrategy(ContentType type, TruncationStrategy strategy) {
        strategies.put(type, strategy);
        logger.debug("注册截断策略: {} -> {}", type, strategy.getClass().getSimpleName());
    }

    public String truncateByExtension(String content, String filePath, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        int effectiveMax = Math.min(maxTokens, GLOBAL_HARD_LIMIT);
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= effectiveMax) {
            return content;
        }
        ContentType type = detectContentType(filePath);
        return truncate(content, type, effectiveMax);
    }

    public String truncate(String content, ContentType type, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        int effectiveMax = Math.min(maxTokens, GLOBAL_HARD_LIMIT);
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= effectiveMax) {
            return content;
        }
        TruncationStrategy strategy = strategies.getOrDefault(type, defaultStrategy);
        String result = strategy.truncate(content, effectiveMax);
        logger.debug("截断完成: 类型={}, 策略={}, 原={}tokens, 目标={}tokens",
                type,
                strategy.getClass().getSimpleName(),
                originalTokens,
                effectiveMax);
        return result;
    }

    public String forceTruncate(String content, ContentType type, int maxTokens) {
        String result = truncate(content, type, maxTokens);
        int tokens = tokenEstimator.estimateTextTokens(result);
        while (tokens > maxTokens && result.length() > 100) {
            int cutIndex = result.length() * 80 / 100;
            result = result.substring(0, cutIndex) + "\n... [强制截断] ...";
            tokens = tokenEstimator.estimateTextTokens(result);
        }
        return result;
    }

    @Deprecated
    public String truncate(String content, String contentType, int maxTokens) {
        return truncateByExtension(content, contentType, maxTokens);
    }

    private ContentType detectContentType(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        if (isCodeFile(extension)) {
            return ContentType.CODE;
        }
        if (isLogFile(extension)) {
            return ContentType.LOG;
        }
        if (isDiffFile(extension)) {
            return ContentType.DIFF;
        }
        return ContentType.PLAIN_TEXT;
    }

    private boolean isCodeFile(String extension) {
        return extension.matches("java|py|js|ts|jsx|tsx|go|rs|c|cpp|h|hpp|rb|php|scala|kt|swift|cs");
    }

    private boolean isLogFile(String extension) {
        return extension.matches("log|txt|out");
    }

    private boolean isDiffFile(String extension) {
        return extension.matches("diff|patch");
    }

    private String getFileExtension(String filePath) {
        if (filePath == null) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        return lastDot >= 0 ? filePath.substring(lastDot + 1) : "";
    }
}
