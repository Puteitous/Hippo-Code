package com.example.agent.logging;

import com.example.agent.llm.model.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TokenMetricsCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenMetricsCollector.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final Path metricsFile;
    private final LocalDate date;
    
    public TokenMetricsCollector(LocalDate date) {
        this.date = date;
        this.metricsFile = LogDirectoryManager.getTokenMetricsFile(date);
        initializeMetricsFile();
    }
    
    private void initializeMetricsFile() {
        try {
            Files.createDirectories(metricsFile.getParent());
            
            if (!Files.exists(metricsFile)) {
                String header = "conversation_id,timestamp,estimated_tokens,prompt_tokens,completion_tokens,total_tokens\n";
                Files.writeString(metricsFile, header, StandardOpenOption.CREATE);
                logger.debug("创建 Token 指标文件: {}", metricsFile);
            }
        } catch (IOException e) {
            logger.error("初始化 Token 指标文件失败: {}", metricsFile, e);
        }
    }
    
    public void recordConversation(String sessionId, LocalDateTime timestamp,
                                   int estimatedTokens, Usage actualUsage) {
        String record = String.format("%s,%s,%d,%d,%d,%d\n",
            sessionId,
            timestamp.format(TIMESTAMP_FORMAT),
            estimatedTokens,
            actualUsage != null ? actualUsage.getPromptTokens() : 0,
            actualUsage != null ? actualUsage.getCompletionTokens() : 0,
            actualUsage != null ? actualUsage.getTotalTokens() : 0
        );
        
        try {
            Files.writeString(metricsFile, record, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.debug("记录 Token 指标: {}", sessionId);
        } catch (IOException e) {
            logger.error("写入 Token 指标失败", e);
        }
    }
    
    public void printDailySummary() {
        try {
            if (!Files.exists(metricsFile)) {
                logger.info("没有找到 {} 的 Token 指标数据", date);
                return;
            }
            
            long totalPrompt = 0;
            long totalCompletion = 0;
            int conversationCount = 0;
            
            for (String line : Files.readAllLines(metricsFile)) {
                if (line.startsWith("conversation_id")) continue;
                
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    conversationCount++;
                    totalPrompt += Long.parseLong(parts[3]);
                    totalCompletion += Long.parseLong(parts[4]);
                }
            }
            
            System.out.println();
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.printf("║   Token 消耗日报 - %10s      ║%n", date);
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.printf("║ 对话次数: %30d ║%n", conversationCount);
            System.out.printf("║ 总输入 Token: %26d ║%n", totalPrompt);
            System.out.printf("║ 总输出 Token: %26d ║%n", totalCompletion);
            System.out.printf("║ 总消耗 Token: %26d ║%n", totalPrompt + totalCompletion);
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.println();
            
            logger.info("Token 日报 - 对话: {}, 总Token: {}", 
                conversationCount, totalPrompt + totalCompletion);
            
        } catch (IOException e) {
            logger.error("读取 Token 指标失败", e);
        }
    }
    
    public Path getMetricsFile() {
        return metricsFile;
    }
}
