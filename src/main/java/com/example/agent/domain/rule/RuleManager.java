package com.example.agent.domain.rule;

import com.example.agent.config.RuleConfig;
import com.example.agent.config.UserResourceManager;
import com.example.agent.memory.MemoryStore;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RuleManager {

    private static final Logger logger = LoggerFactory.getLogger(RuleManager.class);
    private static final int MAX_INJECTED_INDEX_ENTRIES = 50;

    private final TokenEstimator tokenEstimator;
    private final RuleConfig config;
    private MemoryStore memoryStore;

    private String hippoRulesContent;
    private String memoryMdContent;
    private int totalTokens;

    public RuleManager(TokenEstimator tokenEstimator, RuleConfig config) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("TokenEstimator cannot be null");
        }
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new RuleConfig();
        this.hippoRulesContent = "";
        this.memoryMdContent = "";
        this.totalTokens = 0;
    }

    public RuleManager(TokenEstimator tokenEstimator) {
        this(tokenEstimator, null);
    }

    public void setMemoryStore(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    public void loadHippoRules() {
        UserResourceManager.initialize();
        hippoRulesContent = UserResourceManager.loadCombinedRules();
        
        if (!hippoRulesContent.isEmpty()) {
            int tokens = tokenEstimator.estimateTextTokens(hippoRulesContent);
            logger.info("📋 加载分层规则文件，总大小: {} 字符, {} tokens", hippoRulesContent.length(), tokens);
        }
    }

    public void loadMemoryMd() {
        UserResourceManager.initialize();
        memoryMdContent = UserResourceManager.loadCombinedMemory();
        
        if (!memoryMdContent.isEmpty()) {
            int tokens = tokenEstimator.estimateTextTokens(memoryMdContent);
            logger.info("🧠 加载分层记忆文件，总大小: {} 字符, {} tokens", memoryMdContent.length(), tokens);
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

        // 注入长期记忆索引（会话启动时一次注入）
        if (memoryStore != null) {
            String indexText = memoryStore.getIndexText(MAX_INJECTED_INDEX_ENTRIES);
            if (!indexText.isEmpty()) {
                enhanced.append("## 🧠 Long-term Memories\n");
                enhanced.append("Below is a summary of key information from past sessions. ");
                enhanced.append("Do not repeat this verbatim to the user unless asked.\n");
                enhanced.append("```markdown\n").append(indexText).append("\n```\n\n");
                logger.info("🧠 注入长期记忆索引，共 {} 条", MAX_INJECTED_INDEX_ENTRIES);
            }
        }

        String result = enhanced.toString();
        totalTokens = tokenEstimator.estimateTextTokens(result);
        logger.debug("RuleManager 增强系统提示词，共 {} tokens", totalTokens);
        return result;
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
        logger.info("重新加载规则文件...");
        loadHippoRules();
        loadMemoryMd();
    }
}
