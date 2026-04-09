package com.example.agent.context;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.memory.ColdMemory;
import com.example.agent.context.memory.HotMemory;
import com.example.agent.context.memory.WarmMemory;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ContextManager 上下文管理器
 * 
 * 新架构：职责简化
 * - HotMemory：系统提示词增强（项目规则）
 * - WarmMemory：文件缓存 + 智能截断（供 read_file 工具调用）
 * - ColdMemory：按需检索能力（供工具调用，LLM 自主决策）
 */
public class ContextManager {

    private static final Logger logger = LoggerFactory.getLogger(ContextManager.class);

    private final HotMemory hotMemory;
    private final WarmMemory warmMemory;
    private final ColdMemory coldMemory;
    private final TokenEstimator tokenEstimator;
    private final ContextConfig config;

    public ContextManager(TokenEstimator tokenEstimator, ContextConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config != null ? config : new ContextConfig();
        this.hotMemory = new HotMemory(tokenEstimator, this.config.getHotMemory());
        this.warmMemory = new WarmMemory(tokenEstimator, this.config.getWarmMemory());
        this.coldMemory = new ColdMemory(tokenEstimator, this.config.getColdMemory());
    }

    /**
     * 初始化三层记忆
     */
    public void initialize() {
        logger.info("初始化三层记忆架构（新架构）...");

        // 加载 HotMemory（仅增强系统提示词）
        hotMemory.loadHippoRules();
        hotMemory.loadMemoryMd();

        logger.info("记忆架构初始化完成: HotMemory ✅, WarmMemory ✅, ColdMemory ✅");
        logger.info("设计理念：LLM 自主决策，按需调用工具获取上下文");
    }

    /**
     * 获取增强的系统提示词
     */
    public String enhanceSystemPrompt(String baseSystemPrompt) {
        return hotMemory.enhanceSystemPrompt(baseSystemPrompt);
    }

    /**
     * 获取 HotMemory
     */
    public HotMemory getHotMemory() {
        return hotMemory;
    }

    /**
     * 获取 WarmMemory
     */
    public WarmMemory getWarmMemory() {
        return warmMemory;
    }

    /**
     * 获取 ColdMemory
     */
    public ColdMemory getColdMemory() {
        return coldMemory;
    }

    /**
     * 清理所有缓存
     */
    public void cleanup() {
        warmMemory.cleanupCache();
        coldMemory.cleanupCache();
        logger.debug("清理所有记忆缓存");
    }

    /**
     * 获取记忆状态报告
     */
    public String getStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 三层记忆状态报告（新架构） ===\n");
        report.append("\n");
        report.append("设计理念：LLM 自主决策，按需调用工具\n");
        report.append("  - 不再预判用户需要什么文件\n");
        report.append("  - 不再强制注入上下文\n");
        report.append("  - 工具层统一处理截断和缓存\n");
        report.append("\n");
        report.append("HotMemory:\n");
        report.append("  - 规则文件: ").append(config.getHotMemory().getRulesFile()).append("\n");
        report.append("  - 记忆文件: ").append(config.getHotMemory().getMemoryFile()).append("\n");
        report.append("  - 作用: 增强系统提示词\n");
        report.append("\n");
        report.append("WarmMemory:\n");
        report.append("  - 作用: 文件缓存 + 智能截断\n");
        report.append("  - 供 read_file 工具调用\n");
        report.append("  - 缓存大小: ").append(warmMemory.getCacheSize()).append(" 个文件\n");
        report.append("  - 支持语言: Java/Python/JS/TS\n");
        report.append("\n");
        report.append("ColdMemory:\n");
        report.append("  - 作用: 代码库检索工具\n");
        report.append("  - 供 LLM 自主调用\n");
        report.append("  - 缓存大小: ").append(coldMemory.getCacheSize()).append("\n");
        report.append("\n");
        return report.toString();
    }
}
