package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HotMemory 管理类
 * 负责加载和管理 .hipporules 和 MEMORY.md 文件
 */
public class HotMemory {

    private static final Logger logger = LoggerFactory.getLogger(HotMemory.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.HotMemoryConfig config;

    private String hippoRulesContent;
    private String memoryMdContent;
    private int totalTokens;

    public HotMemory(TokenEstimator tokenEstimator, ContextConfig.HotMemoryConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig.HotMemoryConfig();
        this.hippoRulesContent = "";
        this.memoryMdContent = "";
        this.totalTokens = 0;
    }

    public HotMemory(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    /**
     * 加载 .hipporules 文件
     */
    public void loadHippoRules() {
        String rulesFile = config.getRulesFile();
        if (rulesFile == null || rulesFile.isEmpty()) {
            logger.debug("未配置 rules_file，跳过加载");
            return;
        }

        Path filePath = Paths.get(rulesFile);
        if (!Files.exists(filePath)) {
            logger.debug("文件 {} 不存在，跳过加载", rulesFile);
            return;
        }

        try {
            hippoRulesContent = Files.readString(filePath);
            int tokens = tokenEstimator.estimateTextTokens(hippoRulesContent);
            logger.info("加载 .hipporules 文件，大小: {} 字符, {} tokens", hippoRulesContent.length(), tokens);
        } catch (IOException e) {
            logger.warn("加载 .hipporules 文件失败: {}", e.getMessage());
            hippoRulesContent = "";
        }
    }

    /**
     * 加载 MEMORY.md 文件
     */
    public void loadMemoryMd() {
        String memoryFile = config.getMemoryFile();
        if (memoryFile == null || memoryFile.isEmpty()) {
            logger.debug("未配置 memory_file，跳过加载");
            return;
        }

        Path filePath = Paths.get(memoryFile);
        if (!Files.exists(filePath)) {
            logger.debug("文件 {} 不存在，跳过加载", memoryFile);
            return;
        }

        try {
            memoryMdContent = Files.readString(filePath);
            int tokens = tokenEstimator.estimateTextTokens(memoryMdContent);
            logger.info("加载 MEMORY.md 文件，大小: {} 字符, {} tokens", memoryMdContent.length(), tokens);
        } catch (IOException e) {
            logger.warn("加载 MEMORY.md 文件失败: {}", e.getMessage());
            memoryMdContent = "";
        }
    }

    /**
     * 增强系统提示词
     *
     * @param baseSystemPrompt 基础系统提示词
     * @return 增强后的系统提示词
     */
    public String enhanceSystemPrompt(String baseSystemPrompt) {
        if (!config.isInjectAtStartup()) {
            logger.debug("HotMemory 注入已禁用");
            return baseSystemPrompt;
        }

        StringBuilder enhancedPrompt = new StringBuilder();

        // 添加基础系统提示词
        if (baseSystemPrompt != null && !baseSystemPrompt.isEmpty()) {
            enhancedPrompt.append(baseSystemPrompt);
            enhancedPrompt.append("\n\n");
        }

        // 添加 .hipporules 内容
        if (hippoRulesContent != null && !hippoRulesContent.isEmpty()) {
            enhancedPrompt.append("# 项目规则 (.hipporules)\n");
            enhancedPrompt.append(hippoRulesContent);
            enhancedPrompt.append("\n\n");
        }

        // 添加 MEMORY.md 内容
        if (memoryMdContent != null && !memoryMdContent.isEmpty()) {
            enhancedPrompt.append("# 持久化记忆 (MEMORY.md)\n");
            enhancedPrompt.append(memoryMdContent);
            enhancedPrompt.append("\n");
        }

        // 计算总 token 数
        totalTokens = tokenEstimator.estimateTextTokens(enhancedPrompt.toString());
        logger.info("增强后的系统提示词: {} tokens", totalTokens);

        // 检查是否超过最大 token 限制
        if (totalTokens > config.getMaxTokens()) {
            logger.warn("HotMemory 内容超过最大 token 限制 ({} > {})，可能会被裁剪", totalTokens, config.getMaxTokens());
        }

        return enhancedPrompt.toString();
    }

    /**
     * 检查 HotMemory 是否有内容
     */
    public boolean hasContent() {
        return !hippoRulesContent.isEmpty() || !memoryMdContent.isEmpty();
    }

    /**
     * 获取总 token 数
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * 获取 .hipporules 内容
     */
    public String getHippoRulesContent() {
        return hippoRulesContent;
    }

    /**
     * 获取 MEMORY.md 内容
     */
    public String getMemoryMdContent() {
        return memoryMdContent;
    }

    /**
     * 重置 HotMemory
     */
    public void reset() {
        hippoRulesContent = "";
        memoryMdContent = "";
        totalTokens = 0;
    }

    /**
     * 重新加载所有文件
     */
    public void reload() {
        reset();
        loadHippoRules();
        loadMemoryMd();
    }
}
