package com.example.agent.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LogDirectoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LogDirectoryManager.class);
    
    private static final Path LOG_ROOT = Paths.get("logs");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public static Path getLogRoot() {
        return LOG_ROOT;
    }
    
    public static Path getConversationLogDir(LocalDate date) {
        return LOG_ROOT.resolve("conversations")
                      .resolve(date.format(DATE_FORMAT));
    }
    
    public static Path getMetricsDir() {
        return LOG_ROOT.resolve("metrics");
    }
    
    public static Path getConversationLogFile(String conversationId, LocalDate date) {
        return getConversationLogDir(date).resolve("conv_" + conversationId + ".log");
    }
    
    public static Path getTokenMetricsFile(LocalDate date) {
        return getMetricsDir().resolve("tokens_" + date.format(DATE_FORMAT) + ".csv");
    }
    
    public static Path getToolMetricsFile(LocalDate date) {
        return getMetricsDir().resolve("tools_" + date.format(DATE_FORMAT) + ".csv");
    }
    
    public static void ensureDirectoriesExist() {
        try {
            Path conversationDir = getConversationLogDir(LocalDate.now());
            Path metricsDir = getMetricsDir();
            
            if (!java.nio.file.Files.exists(conversationDir)) {
                java.nio.file.Files.createDirectories(conversationDir);
                logger.debug("创建对话日志目录: {}", conversationDir);
            }
            
            if (!java.nio.file.Files.exists(metricsDir)) {
                java.nio.file.Files.createDirectories(metricsDir);
                logger.debug("创建指标日志目录: {}", metricsDir);
            }
            
            logger.info("日志目录初始化完成");
        } catch (Exception e) {
            logger.error("创建日志目录失败", e);
        }
    }
}
