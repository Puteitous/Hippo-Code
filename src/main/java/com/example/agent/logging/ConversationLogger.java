package com.example.agent.logging;

import com.example.agent.llm.model.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class ConversationLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationLogger.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final String conversationId;
    private final Path logFile;
    private final LocalDateTime startTime;
    private final AtomicInteger totalInputTokens = new AtomicInteger(0);
    private final AtomicInteger totalOutputTokens = new AtomicInteger(0);
    private final AtomicInteger totalToolCalls = new AtomicInteger(0);
    
    public ConversationLogger(String conversationId, Path logFile) {
        this.conversationId = conversationId;
        this.logFile = logFile;
        this.startTime = LocalDateTime.now();
        
        try {
            Files.createDirectories(logFile.getParent());
            writeHeader();
            logger.debug("创建对话日志: {}", logFile);
        } catch (IOException e) {
            logger.error("创建对话日志文件失败: {}", logFile, e);
        }
    }
    
    private void writeHeader() throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("═".repeat(80)).append("\n");
        header.append("对话 ID: ").append(conversationId).append("\n");
        header.append("开始时间: ").append(startTime.format(TIMESTAMP_FORMAT)).append("\n");
        header.append("═".repeat(80)).append("\n\n");
        
        Files.writeString(logFile, header.toString(), 
            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
    
    public void logUserInput(String input, int estimatedTokens) {
        totalInputTokens.addAndGet(estimatedTokens);
        
        StringBuilder entry = new StringBuilder();
        entry.append("\n┌─ 用户输入 ─────────────────────────────────\n");
        entry.append("│ 时间: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        entry.append("│ 估算 Token: ").append(estimatedTokens).append("\n");
        entry.append("├────────────────────────────────────────────\n");
        if (input != null && !input.isEmpty()) {
            entry.append("│ ").append(input.replace("\n", "\n│ ")).append("\n");
        } else {
            entry.append("│ (空输入)\n");
        }
        entry.append("└────────────────────────────────────────────\n");
        
        writeToFile(entry.toString());
        logger.debug("记录用户输入，估算 token: {}", estimatedTokens);
    }
    
    public void logAiResponse(String response, Usage usage) {
        if (usage != null) {
            totalInputTokens.addAndGet(usage.getPromptTokens());
            totalOutputTokens.addAndGet(usage.getCompletionTokens());
        }
        
        StringBuilder entry = new StringBuilder();
        entry.append("\n┌─ AI 响应 ─────────────────────────────────\n");
        entry.append("│ 时间: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        if (usage != null) {
            entry.append("│ Token 使用: Prompt=").append(usage.getPromptTokens())
                 .append(", Completion=").append(usage.getCompletionTokens())
                 .append(", Total=").append(usage.getTotalTokens()).append("\n");
        }
        entry.append("├────────────────────────────────────────────\n");
        if (response != null && !response.isEmpty()) {
            entry.append("│ ").append(response.replace("\n", "\n│ ")).append("\n");
        }
        entry.append("└────────────────────────────────────────────\n");
        
        writeToFile(entry.toString());
        logger.debug("记录 AI 响应，实际 token: {}", usage);
    }
    
    public void logToolCall(String toolName, String args, String result, 
                           long duration, boolean success) {
        totalToolCalls.incrementAndGet();
        
        StringBuilder entry = new StringBuilder();
        entry.append("\n┌─ 工具调用 ────────────────────────────────\n");
        entry.append("│ 时间: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        entry.append("│ 工具: ").append(toolName).append("\n");
        entry.append("│ 耗时: ").append(duration).append("ms\n");
        entry.append("│ 状态: ").append(success ? "✅ 成功" : "❌ 失败").append("\n");
        entry.append("├────────────────────────────────────────────\n");
        entry.append("│ 参数: ").append(args).append("\n");
        entry.append("├────────────────────────────────────────────\n");
        entry.append("│ 结果: ").append(truncate(result, 500)).append("\n");
        entry.append("└────────────────────────────────────────────\n");
        
        writeToFile(entry.toString());
        logger.debug("记录工具调用: {} ({}ms)", toolName, duration);
    }
    
    public void logSummary() {
        LocalDateTime endTime = LocalDateTime.now();
        long duration = Duration.between(startTime, endTime).toSeconds();
        
        StringBuilder summary = new StringBuilder();
        summary.append("\n\n").append("═".repeat(80)).append("\n");
        summary.append("对话摘要\n");
        summary.append("═".repeat(80)).append("\n");
        summary.append("对话 ID: ").append(conversationId).append("\n");
        summary.append("开始时间: ").append(startTime.format(TIMESTAMP_FORMAT)).append("\n");
        summary.append("结束时间: ").append(endTime.format(TIMESTAMP_FORMAT)).append("\n");
        summary.append("总耗时: ").append(duration).append(" 秒\n");
        summary.append("总输入 Token: ").append(totalInputTokens.get()).append("\n");
        summary.append("总输出 Token: ").append(totalOutputTokens.get()).append("\n");
        summary.append("总 Token: ").append(totalInputTokens.get() + totalOutputTokens.get()).append("\n");
        summary.append("工具调用次数: ").append(totalToolCalls.get()).append("\n");
        summary.append("═".repeat(80)).append("\n");
        
        writeToFile(summary.toString());
        logger.info("对话摘要 - ID: {}, 总Token: {}, 工具调用: {}", 
            conversationId, totalInputTokens.get() + totalOutputTokens.get(), totalToolCalls.get());
    }
    
    public void logDebug(String message) {
        StringBuilder entry = new StringBuilder();
        entry.append("\n┌─ DEBUG ───────────────────────────────────\n");
        entry.append("│ 时间: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        entry.append("├────────────────────────────────────────────\n");
        entry.append("│ ").append(message.replace("\n", "\n│ ")).append("\n");
        entry.append("└────────────────────────────────────────────\n");
        
        writeToFile(entry.toString());
        logger.debug("DEBUG: {}", message);
    }
    
    private void writeToFile(String content) {
        try {
            Files.writeString(logFile, content, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("写入对话日志失败: {}", logFile, e);
        }
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "... (已截断)";
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public int getTotalInputTokens() {
        return totalInputTokens.get();
    }
    
    public int getTotalOutputTokens() {
        return totalOutputTokens.get();
    }
    
    public int getTotalToolCalls() {
        return totalToolCalls.get();
    }
}
