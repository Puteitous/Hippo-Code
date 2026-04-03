package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ToolExecutionLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final ConcurrentHashMap<String, ToolMetrics> metricsMap;
    private final Path logFile;
    private final boolean enableFileLogging;
    
    public ToolExecutionLogger() {
        this(null, false);
    }
    
    public ToolExecutionLogger(Path logFile, boolean enableFileLogging) {
        this.metricsMap = new ConcurrentHashMap<>();
        this.logFile = logFile;
        this.enableFileLogging = enableFileLogging;
        
        if (enableFileLogging && logFile != null) {
            try {
                Path parentDir = logFile.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
            } catch (IOException e) {
                System.err.println("无法创建日志目录: " + e.getMessage());
            }
        }
    }
    
    public void log(String toolName, JsonNode arguments, String result, 
                   long duration, boolean success) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(TIMESTAMP_FORMAT);
        
        updateMetrics(toolName, duration, success);
        
        if (enableFileLogging && logFile != null) {
            writeToFile(timestamp, toolName, arguments, result, duration, success);
        }
        
        printToConsole(timestamp, toolName, duration, success);
    }
    
    private void updateMetrics(String toolName, long duration, boolean success) {
        metricsMap.computeIfAbsent(toolName, k -> new ToolMetrics(toolName))
                  .record(duration, success);
    }
    
    private void writeToFile(String timestamp, String toolName, JsonNode arguments,
                            String result, long duration, boolean success) {
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ");
        logEntry.append(toolName).append(" | ");
        logEntry.append("duration: ").append(duration).append("ms | ");
        logEntry.append("status: ").append(success ? "SUCCESS" : "FAILED").append("\n");
        
        try {
            Files.writeString(logFile, logEntry.toString(), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("写入日志文件失败: " + e.getMessage());
        }
    }
    
    private void printToConsole(String timestamp, String toolName, 
                               long duration, boolean success) {
        String status = success ? "✅" : "❌";
        String color = success ? "\u001B[32m" : "\u001B[31m";
        String reset = "\u001B[0m";
        
        System.out.println(color + "[" + timestamp + "] " + status + " " + 
                          toolName + " (" + duration + "ms)" + reset);
    }
    
    public ToolMetrics getMetrics(String toolName) {
        return metricsMap.get(toolName);
    }
    
    public void printSummary() {
        System.out.println("\n工具执行统计");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.printf("%-20s %8s %12s %10s %10s%n", 
            "工具名称", "调用次数", "平均耗时", "成功率", "失败次数");
        System.out.println("─────────────────────────────────────────────────────────────");
        
        metricsMap.values().stream()
            .sorted((a, b) -> Long.compare(b.getTotalDuration(), a.getTotalDuration()))
            .forEach(metrics -> {
                System.out.printf("%-20s %8d %10.1fms %9.1f%% %10d%n",
                    metrics.getToolName(),
                    metrics.getCallCount(),
                    metrics.getAverageDuration(),
                    metrics.getSuccessRate() * 100,
                    metrics.getFailureCount()
                );
            });
        
        System.out.println("─────────────────────────────────────────────────────────────");
    }
    
    public static class ToolMetrics {
        private final String toolName;
        private final AtomicInteger callCount;
        private final AtomicInteger successCount;
        private final AtomicInteger failureCount;
        private final java.util.concurrent.atomic.AtomicLong totalDuration;
        
        public ToolMetrics(String toolName) {
            this.toolName = toolName;
            this.callCount = new AtomicInteger(0);
            this.successCount = new AtomicInteger(0);
            this.failureCount = new AtomicInteger(0);
            this.totalDuration = new java.util.concurrent.atomic.AtomicLong(0);
        }
        
        public void record(long duration, boolean success) {
            callCount.incrementAndGet();
            totalDuration.addAndGet(duration);
            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }
        
        public String getToolName() {
            return toolName;
        }
        
        public int getCallCount() {
            return callCount.get();
        }
        
        public int getSuccessCount() {
            return successCount.get();
        }
        
        public int getFailureCount() {
            return failureCount.get();
        }
        
        public long getTotalDuration() {
            return totalDuration.get();
        }
        
        public double getAverageDuration() {
            int count = callCount.get();
            return count == 0 ? 0 : (double) totalDuration.get() / count;
        }
        
        public double getSuccessRate() {
            int count = callCount.get();
            return count == 0 ? 0 : (double) successCount.get() / count;
        }
    }
}
