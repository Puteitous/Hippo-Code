package com.example.agent.context;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.memory.ColdMemory;
import com.example.agent.context.memory.HotMemory;
import com.example.agent.context.memory.WarmMemory;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ContextManager 上下文管理器
 * 统一管理 HotMemory、WarmMemory、ColdMemory 三层记忆
 */
public class ContextManager {

    private static final Logger logger = LoggerFactory.getLogger(ContextManager.class);

    private final HotMemory hotMemory;
    private final WarmMemory warmMemory;
    private final ColdMemory coldMemory;
    private final TokenEstimator tokenEstimator;
    private final ContextConfig config;

    // 性能监控
    private final MemoryMetrics metrics;

    public ContextManager(TokenEstimator tokenEstimator, ContextConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.config = config;
        this.hotMemory = new HotMemory(tokenEstimator, config.getHotMemory());
        this.warmMemory = new WarmMemory(tokenEstimator, config.getWarmMemory());
        this.coldMemory = new ColdMemory(tokenEstimator, config.getColdMemory());
        this.metrics = new MemoryMetrics();
    }

    /**
     * 初始化三层记忆
     */
    public void initialize() {
        logger.info("初始化三层记忆架构...");

        // 加载 HotMemory
        hotMemory.loadHippoRules();
        hotMemory.loadMemoryMd();

        logger.info("三层记忆架构初始化完成");
    }

    /**
     * 获取增强的系统提示词
     */
    public String enhanceSystemPrompt(String baseSystemPrompt) {
        return hotMemory.enhanceSystemPrompt(baseSystemPrompt);
    }

    /**
     * 处理用户输入，整合三层记忆
     *
     * @param userInput 用户输入
     * @return 整合后的消息列表
     */
    public List<Message> processUserInput(String userInput) {
        List<Message> messages = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // 1. 处理 WarmMemory @ 引用
        List<Message> warmMessages = warmMemory.processReferences(userInput);
        messages.addAll(warmMessages);
        metrics.recordWarmMemoryTime(System.currentTimeMillis() - startTime);
        metrics.recordWarmMemoryCount(warmMessages.size());

        // 2. 处理 ColdMemory 检索
        long coldStartTime = System.currentTimeMillis();
        List<Message> coldMessages = coldMemory.retrieve(userInput, config.getColdMemory().getMaxTokens());
        messages.addAll(coldMessages);
        metrics.recordColdMemoryTime(System.currentTimeMillis() - coldStartTime);
        metrics.recordColdMemoryCount(coldMessages.size());

        logger.debug("三层记忆处理完成: WarmMemory={}, ColdMemory={}, 总耗时={}ms",
                warmMessages.size(), coldMessages.size(), System.currentTimeMillis() - startTime);

        return messages;
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
     * 获取性能指标
     */
    public MemoryMetrics getMetrics() {
        return metrics;
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
     * 获取三层记忆状态报告
     */
    public String getStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 三层记忆状态报告 ===\n");
        report.append("HotMemory:\n");
        report.append("  - 规则文件: ").append(config.getHotMemory().getRulesFile()).append("\n");
        report.append("  - 记忆文件: ").append(config.getHotMemory().getMemoryFile()).append("\n");
        report.append("  - 最大 tokens: ").append(config.getHotMemory().getMaxTokens()).append("\n");
        report.append("\nWarmMemory:\n");
        report.append("  - @引用启用: ").append(config.getWarmMemory().isAtReferenceEnabled()).append("\n");
        report.append("  - 缓存大小: ").append(warmMemory.getCacheSize()).append("\n");
        report.append("  - 最大引用数: ").append(config.getWarmMemory().getMaxRefsPerMessage()).append("\n");
        report.append("\nColdMemory:\n");
        report.append("  - 启用状态: ").append(config.getColdMemory().isEnabled()).append("\n");
        report.append("  - 缓存大小: ").append(coldMemory.getCacheSize()).append("\n");
        report.append("  - 最大结果数: ").append(config.getColdMemory().getMaxResults()).append("\n");
        report.append("\n性能指标:\n");
        report.append(metrics.getReport());
        return report.toString();
    }

    /**
     * 内存性能指标
     */
    public static class MemoryMetrics {
        private long warmMemoryTotalTime = 0;
        private long coldMemoryTotalTime = 0;
        private int warmMemoryTotalCount = 0;
        private int coldMemoryTotalCount = 0;
        private int requestCount = 0;

        public void recordWarmMemoryTime(long timeMs) {
            warmMemoryTotalTime += timeMs;
        }

        public void recordColdMemoryTime(long timeMs) {
            coldMemoryTotalTime += timeMs;
        }

        public void recordWarmMemoryCount(int count) {
            warmMemoryTotalCount += count;
        }

        public void recordColdMemoryCount(int count) {
            coldMemoryTotalCount += count;
        }

        public void recordRequest() {
            requestCount++;
        }

        public String getReport() {
            StringBuilder report = new StringBuilder();
            report.append("  - 请求次数: ").append(requestCount).append("\n");
            report.append("  - WarmMemory 平均耗时: ")
                    .append(requestCount > 0 ? warmMemoryTotalTime / requestCount : 0).append("ms\n");
            report.append("  - ColdMemory 平均耗时: ")
                    .append(requestCount > 0 ? coldMemoryTotalTime / requestCount : 0).append("ms\n");
            report.append("  - WarmMemory 平均消息数: ")
                    .append(requestCount > 0 ? warmMemoryTotalCount / requestCount : 0).append("\n");
            report.append("  - ColdMemory 平均消息数: ")
                    .append(requestCount > 0 ? coldMemoryTotalCount / requestCount : 0).append("\n");
            return report.toString();
        }
    }
}
