package com.example.agent.tools.stats;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonRepairStats {

    private final AtomicInteger totalAttempts = new AtomicInteger(0);
    private final AtomicInteger totalRepairs = new AtomicInteger(0);
    private final AtomicInteger totalFailures = new AtomicInteger(0);

    private final ConcurrentHashMap<String, AtomicInteger> repairByTool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> repairByReason = new ConcurrentHashMap<>();

    public void recordAttempt() {
        totalAttempts.incrementAndGet();
    }

    public void recordRepair(String toolName, String reason) {
        totalRepairs.incrementAndGet();
        repairByTool.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();
        repairByReason.computeIfAbsent(reason, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void recordFailure(String toolName) {
        totalFailures.incrementAndGet();
    }

    public int getTotalAttempts() {
        return totalAttempts.get();
    }

    public int getTotalRepairs() {
        return totalRepairs.get();
    }

    public int getTotalFailures() {
        return totalFailures.get();
    }

    public double getRepairRate() {
        int attempts = totalAttempts.get();
        return attempts > 0 ? (totalRepairs.get() * 100.0 / attempts) : 0.0;
    }

    public double getFailureRate() {
        int attempts = totalAttempts.get();
        return attempts > 0 ? (totalFailures.get() * 100.0 / attempts) : 0.0;
    }

    public int getRepairCountByTool(String toolName) {
        return repairByTool.getOrDefault(toolName, new AtomicInteger(0)).get();
    }

    public int getRepairCountByReason(String reason) {
        return repairByReason.getOrDefault(reason, new AtomicInteger(0)).get();
    }

    public String getSummary() {
        int attempts = totalAttempts.get();
        int repairs = totalRepairs.get();
        int failures = totalFailures.get();
        double repairRate = getRepairRate();
        double failureRate = getFailureRate();

        return String.format("""
                
                === 🔧 JSON 修复统计 ===
                总解析尝试: %d
                  - 直接解析成功: %d (%.1f%%)
                  - 需要修复: %d (%.1f%%)
                  - 修复失败: %d (%.1f%%)
                """,
                attempts,
                attempts - repairs, (100.0 - repairRate - failureRate),
                repairs, repairRate,
                failures, failureRate
        );
    }

    public String getDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary());
        sb.append("\n=== 按工具统计 ===\n");

        repairByTool.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .forEach(entry -> {
                    sb.append(String.format("  %s: %d 次修复\n", entry.getKey(), entry.getValue().get()));
                });

        sb.append("\n=== 按原因统计 ===\n");
        repairByReason.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .forEach(entry -> {
                    sb.append(String.format("  %s: %d 次\n", entry.getKey(), entry.getValue().get()));
                });

        return sb.toString();
    }
}
