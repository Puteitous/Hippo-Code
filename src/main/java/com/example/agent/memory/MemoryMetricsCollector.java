package com.example.agent.memory;

import com.example.agent.core.event.Event;
import com.example.agent.core.event.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 记忆系统监控指标收集器
 * 
 * 收集以下指标：
 * 1. 检索命中率
 * 2. 降级次数（向量 → 关键词）
 * 3. 注入成功率
 */
public class MemoryMetricsCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryMetricsCollector.class);
    private static final int REPORT_INTERVAL_MS = 60000; // 每分钟报告一次
    
    // 检索指标
    private final AtomicInteger vectorSearchCount = new AtomicInteger(0);
    private final AtomicInteger vectorSearchHitCount = new AtomicInteger(0);
    private final AtomicInteger keywordFallbackCount = new AtomicInteger(0);
    private final AtomicInteger keywordSearchHitCount = new AtomicInteger(0);
    
    // 注入指标
    private final AtomicInteger injectionSuccessCount = new AtomicInteger(0);
    private final AtomicInteger injectionEmptyCount = new AtomicInteger(0);
    
    // 报告
    private volatile long lastReportTime = System.currentTimeMillis();
    
    public MemoryMetricsCollector() {
        logger.info("📊 记忆系统监控指标收集器已初始化");
    }
    
    /**
     * 记录向量检索命中
     */
    public void recordVectorSearchHit() {
        vectorSearchCount.incrementAndGet();
        vectorSearchHitCount.incrementAndGet();
        maybeReport();
    }
    
    /**
     * 记录向量检索未命中（回退到关键词）
     */
    public void recordVectorSearchMiss() {
        vectorSearchCount.incrementAndGet();
        keywordFallbackCount.incrementAndGet();
        maybeReport();
    }
    
    /**
     * 记录关键词检索命中
     */
    public void recordKeywordSearchHit() {
        keywordSearchHitCount.incrementAndGet();
        maybeReport();
    }
    
    /**
     * 记录注入成功
     */
    public void recordInjectionSuccess() {
        injectionSuccessCount.incrementAndGet();
        maybeReport();
    }
    
    /**
     * 记录注入为空（无相关记忆）
     */
    public void recordInjectionEmpty() {
        injectionEmptyCount.incrementAndGet();
        maybeReport();
    }
    
    /**
     * 获取平均检索耗时
     */
    public double getAverageSearchTime() {
        int count = vectorSearchCount.get();
        return count > 0 ? (double) vectorSearchHitCount.get() / count : 0.0;
    }
    
    /**
     * 获取检索命中率
     */
    public double getSearchHitRate() {
        int count = vectorSearchCount.get();
        return count > 0 ? (double) vectorSearchHitCount.get() / count : 0.0;
    }
    
    /**
     * 获取降级率
     */
    public double getFallbackRate() {
        int count = vectorSearchCount.get();
        return count > 0 ? (double) keywordFallbackCount.get() / count : 0.0;
    }
    
    /**
     * 获取注入成功率
     */
    public double getInjectionSuccessRate() {
        int total = injectionSuccessCount.get() + injectionEmptyCount.get();
        return total > 0 ? (double) injectionSuccessCount.get() / total : 0.0;
    }
    
    /**
     * 生成指标摘要
     */
    public String getSummary() {
        return String.format("""
            
            === 🧠 记忆系统指标 ===
            检索: %d 次
              - 命中率: %.1f%%
              - 降级次数: %d
            关键词检索: %d 次命中
            记忆注入: %d 次成功, %d 次为空
            """,
            vectorSearchCount.get(),
            getSearchHitRate() * 100,
            keywordFallbackCount.get(),
            keywordSearchHitCount.get(),
            injectionSuccessCount.get(),
            injectionEmptyCount.get()
        );
    }
    
    /**
     * 定期报告指标
     */
    private void maybeReport() {
        long now = System.currentTimeMillis();
        if (now - lastReportTime > REPORT_INTERVAL_MS) {
            logger.info(getSummary());
            lastReportTime = now;
        }
    }
    
    /**
     * 重置所有指标
     */
    public void reset() {
        vectorSearchCount.set(0);
        vectorSearchHitCount.set(0);
        keywordFallbackCount.set(0);
        keywordSearchHitCount.set(0);
        injectionSuccessCount.set(0);
        injectionEmptyCount.set(0);
        lastReportTime = System.currentTimeMillis();
        logger.info("记忆系统指标已重置");
    }
}
