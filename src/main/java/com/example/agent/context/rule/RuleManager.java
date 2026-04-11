package com.example.agent.context.rule;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RuleManager {

    private static final Logger logger = LoggerFactory.getLogger(RuleManager.class);

    private final TokenEstimator tokenEstimator;
    private final ContextConfig.HotMemoryConfig config;

    private String hippoRulesContent;
    private String memoryMdContent;
    private int totalTokens;

    public RuleManager(TokenEstimator tokenEstimator, ContextConfig.HotMemoryConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig.HotMemoryConfig();
        this.hippoRulesContent = "";
        this.memoryMdContent = "";
        this.totalTokens = 0;
    }

    public RuleManager(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

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

    public String enhanceSystemPrompt(String baseSystemPrompt) {
        if (!config.isInjectAtStartup()) {
            logger.debug("RuleManager 注入已禁用");
            return baseSystemPrompt;
        }

        StringBuilder enhanced = new StringBuilder();
        if (baseSystemPrompt != null && !baseSystemPrompt.isEmpty()) {
            enhanced.append(baseSystemPrompt).append("\n\n");
        }

        enhanced.append("=== 项目规则 ===\n");
        if (!hippoRulesContent.isEmpty()) {
            enhanced.append(hippoRulesContent).append("\n\n");
        }
        if (!memoryMdContent.isEmpty()) {
            enhanced.append("=== 项目上下文记忆 ===\n");
            enhanced.append(memoryMdContent).append("\n\n");
        }

        totalTokens = tokenEstimator.estimateTextTokens(enhanced.toString());
        logger.debug("注入规则到系统提示词，约 {} tokens", totalTokens);

        return enhanced.toString();
    }

    public String getHippoRulesContent() {
        return hippoRulesContent;
    }

    public String getMemoryMdContent() {
        return memoryMdContent;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void reload() {
        loadHippoRules();
        loadMemoryMd();
        logger.info("规则文件已重新加载");
    }
}
