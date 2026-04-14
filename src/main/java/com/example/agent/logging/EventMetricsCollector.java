package com.example.agent.logging;

import com.example.agent.core.event.EventBus;
import com.example.agent.core.event.IntentRecognizedEvent;
import com.example.agent.core.event.LlmRequestEvent;
import com.example.agent.core.event.MessageEvent;
import com.example.agent.core.event.ToolExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EventMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(EventMetricsCollector.class);

    private final AtomicInteger totalToolCalls = new AtomicInteger(0);
    private final AtomicInteger successfulToolCalls = new AtomicInteger(0);
    private final AtomicInteger failedToolCalls = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> toolUsage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongSummaryStatistics> toolLatency = new ConcurrentHashMap<>();

    private final AtomicInteger totalLlmRequests = new AtomicInteger(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    private final LocalDate date;

    public EventMetricsCollector(LocalDate date) {
        this.date = date;
        registerSubscribers();
        logger.info("事件驱动指标收集器已初始化 ✅");
    }

    private void registerSubscribers() {
        EventBus.subscribe(ToolExecutedEvent.class, this::onToolExecuted);
        EventBus.subscribe(LlmRequestEvent.class, this::onLlmRequest);
        EventBus.subscribe(MessageEvent.class, this::onMessage);
        EventBus.subscribe(IntentRecognizedEvent.class, this::onIntentRecognized);
    }

    private void onToolExecuted(ToolExecutedEvent event) {
        totalToolCalls.incrementAndGet();
        if (event.success()) {
            successfulToolCalls.incrementAndGet();
        } else {
            failedToolCalls.incrementAndGet();
        }

        toolUsage.computeIfAbsent(event.toolName(), k -> new AtomicInteger(0)).incrementAndGet();
        toolLatency.computeIfAbsent(event.toolName(), k -> new LongSummaryStatistics())
                .accept(event.durationMs());
    }

    private void onLlmRequest(LlmRequestEvent event) {
        totalLlmRequests.incrementAndGet();
        totalPromptTokens.addAndGet(event.promptTokens());
        totalCompletionTokens.addAndGet(event.completionTokens());
    }

    private void onMessage(MessageEvent event) {
    }

    private void onIntentRecognized(IntentRecognizedEvent event) {
        logger.debug("识别意图: {}, 置信度: {:.2f}", event.intentType(), event.confidence());
    }

    public String getSummary() {
        long totalTokens = totalPromptTokens.get() + totalCompletionTokens.get();
        int successRate = totalToolCalls.get() > 0
                ? (int) (successfulToolCalls.get() * 100.0 / totalToolCalls.get())
                : 0;

        return String.format("""
                
                === 📊 会话统计 ===
                工具调用: %d 次 (成功率 %d%%)
                  - 成功: %d, 失败: %d
                  - 最常用: %s
                LLM 请求: %d 次
                  - 总 Token: %d (Prompt: %d, Completion: %d)
                """,
                totalToolCalls.get(),
                successRate,
                successfulToolCalls.get(),
                failedToolCalls.get(),
                getMostUsedTool(),
                totalLlmRequests.get(),
                totalTokens,
                totalPromptTokens.get(),
                totalCompletionTokens.get()
        );
    }

    private String getMostUsedTool() {
        return toolUsage.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue(
                        java.util.Comparator.comparingInt(AtomicInteger::get)))
                .map(e -> e.getKey() + " (" + e.getValue().get() + "次)")
                .orElse("无");
    }
}