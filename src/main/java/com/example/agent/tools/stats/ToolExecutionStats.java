package com.example.agent.tools.stats;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ToolExecutionStats {

    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger successCalls = new AtomicInteger(0);
    private final AtomicInteger failureCalls = new AtomicInteger(0);
    private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);
    private final AtomicLong totalJsonRepairs = new AtomicLong(0);
    private final AtomicLong totalNormalizations = new AtomicLong(0);
    private final AtomicLong totalValidationErrors = new AtomicLong(0);

    private final ConcurrentHashMap<String, AtomicInteger> toolCallCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> toolExecutionTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> toolJsonRepairs = new ConcurrentHashMap<>();

    public void recordCall(String toolName, boolean success, long executionTimeMs) {
        totalCalls.incrementAndGet();
        if (success) {
            successCalls.incrementAndGet();
        } else {
            failureCalls.incrementAndGet();
        }
        totalExecutionTimeMs.addAndGet(executionTimeMs);

        toolCallCounts.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();
        toolExecutionTimes.computeIfAbsent(toolName, k -> new AtomicLong(0)).addAndGet(executionTimeMs);
    }

    public void recordJsonRepair(String toolName) {
        totalJsonRepairs.incrementAndGet();
        toolJsonRepairs.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void recordNormalization(String toolName) {
        totalNormalizations.incrementAndGet();
    }

    public void recordValidationError(String toolName) {
        totalValidationErrors.incrementAndGet();
    }

    public int getTotalCalls() {
        return totalCalls.get();
    }

    public int getSuccessCalls() {
        return successCalls.get();
    }

    public int getFailureCalls() {
        return failureCalls.get();
    }

    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs.get();
    }

    public long getAverageExecutionTimeMs() {
        int total = totalCalls.get();
        return total > 0 ? totalExecutionTimeMs.get() / total : 0;
    }

    public long getTotalJsonRepairs() {
        return totalJsonRepairs.get();
    }

    public long getTotalNormalizations() {
        return totalNormalizations.get();
    }

    public long getTotalValidationErrors() {
        return totalValidationErrors.get();
    }

    public int getToolCallCount(String toolName) {
        return toolCallCounts.getOrDefault(toolName, new AtomicInteger(0)).get();
    }

    public long getToolExecutionTime(String toolName) {
        return toolExecutionTimes.getOrDefault(toolName, new AtomicLong(0)).get();
    }

    public int getToolJsonRepairCount(String toolName) {
        return toolJsonRepairs.getOrDefault(toolName, new AtomicInteger(0)).get();
    }

    public String getSummary() {
        int total = totalCalls.get();
        int success = successCalls.get();
        int failure = failureCalls.get();
        double successRate = total > 0 ? (success * 100.0 / total) : 0.0;
        long avgTime = getAverageExecutionTimeMs();

        return String.format("""
                
                === 📊 工具执行统计 ===
                总调用次数: %d
                  - 成功: %d (%.1f%%)
                  - 失败: %d (%.1f%%)
                平均执行时间: %d ms
                JSON 修复次数: %d
                参数规范化次数: %d
                参数验证失败次数: %d
                """,
                total,
                success, successRate,
                failure, (100.0 - successRate),
                avgTime,
                totalJsonRepairs.get(),
                totalNormalizations.get(),
                totalValidationErrors.get()
        );
    }

    public String getDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary());
        sb.append("\n=== 🔧 各工具详细统计 ===\n");

        toolCallCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .forEach(entry -> {
                    String toolName = entry.getKey();
                    int calls = entry.getValue().get();
                    long totalTime = toolExecutionTimes.getOrDefault(toolName, new AtomicLong(0)).get();
                    long avgTime = calls > 0 ? totalTime / calls : 0;
                    int repairs = getToolJsonRepairCount(toolName);

                    sb.append(String.format("  %s: %d 次调用, 平均 %d ms, JSON 修复 %d 次\n",
                            toolName, calls, avgTime, repairs));
                });

        return sb.toString();
    }
}
