package com.example.agent.logging;

import com.example.agent.core.event.EventBus;
import com.example.agent.core.event.LlmRequestEvent;
import com.example.agent.llm.pricing.LlmPricing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CostMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(CostMetricsCollector.class);

    private final LocalDate date;

    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);

    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    private final AtomicReference<BigDecimal> totalCostUsd = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> totalCostCny = new AtomicReference<>(BigDecimal.ZERO);

    private final Map<String, AtomicLong> modelTokenUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<BigDecimal>> modelCosts = new ConcurrentHashMap<>();

    public CostMetricsCollector(LocalDate date) {
        this.date = date;
        registerSubscribers();
        logger.info("LLM 成本指标收集器已初始化 ✅");
    }

    public CostMetricsCollector() {
        this(LocalDate.now());
    }

    private void registerSubscribers() {
        EventBus.subscribe(LlmRequestEvent.class, this::onLlmRequest);
    }

    private void onLlmRequest(LlmRequestEvent event) {
        totalRequests.incrementAndGet();

        if (event.success()) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }

        totalPromptTokens.addAndGet(event.promptTokens());
        totalCompletionTokens.addAndGet(event.completionTokens());

        LlmPricing.Cost cost = LlmPricing.calculateCost(
                event.model(),
                event.promptTokens(),
                event.completionTokens()
        );

        if (!cost.isUnknown()) {
            if ("¥".equals(cost.getCurrency())) {
                totalCostCny.accumulateAndGet(cost.totalCost(), BigDecimal::add);
            } else {
                totalCostUsd.accumulateAndGet(cost.totalCost(), BigDecimal::add);
            }

            modelTokenUsage.computeIfAbsent(event.model(), k -> new AtomicLong(0))
                    .addAndGet(cost.totalTokens());

            modelCosts.computeIfAbsent(event.model(), k -> new AtomicReference<>(BigDecimal.ZERO))
                    .accumulateAndGet(cost.totalCost(), BigDecimal::add);
        }

        logger.debug("[LLM 成本] {} {} - {}ms",
                event.success() ? "✅" : "❌",
                event.model(),
                event.latencyMs());
        logger.debug("       Tokens: In={}, Out={}, Cost: {}",
                event.promptTokens(),
                event.completionTokens(),
                cost.format());
    }

    public String getSummary() {
        long totalTokens = totalPromptTokens.get() + totalCompletionTokens.get();
        int successRate = totalRequests.get() > 0
                ? (int) (successfulRequests.get() * 100.0 / totalRequests.get())
                : 0;

        StringBuilder sb = new StringBuilder();

        sb.append(String.format("""
                
                === 💰 LLM 成本统计 ===
                请求次数: %d 次 (成功率 %d%%)
                  - 成功: %d, 失败: %d
                  - 总 Token: %d (Prompt: %d, Completion: %d)
                """,
                totalRequests.get(),
                successRate,
                successfulRequests.get(),
                failedRequests.get(),
                totalTokens,
                totalPromptTokens.get(),
                totalCompletionTokens.get()
        ));

        if (totalCostUsd.get().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("  - 总花费(USD): $ %.6f%n",
                    totalCostUsd.get().setScale(6, RoundingMode.HALF_UP)));
        }
        if (totalCostCny.get().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("  - 总花费(CNY): ¥ %.6f%n",
                    totalCostCny.get().setScale(6, RoundingMode.HALF_UP)));
        }

        if (!modelCosts.isEmpty()) {
            sb.append("\n  按模型细分:\n");
            modelCosts.forEach((model, cost) -> {
                long tokens = modelTokenUsage.getOrDefault(model, new AtomicLong(0)).get();
                LlmPricing.Cost c = LlmPricing.calculateCost(model, 0, 0);
                sb.append(String.format("    - %s: %d tokens, %s %.6f%n",
                        model, tokens, c.getCurrency(), cost.get().setScale(6, RoundingMode.HALF_UP)));
            });
        }

        return sb.toString();
    }

    public BigDecimal getTotalCostUsd() {
        return totalCostUsd.get();
    }

    public BigDecimal getTotalCostCny() {
        return totalCostCny.get();
    }

    public long getTotalTokens() {
        return totalPromptTokens.get() + totalCompletionTokens.get();
    }
}
