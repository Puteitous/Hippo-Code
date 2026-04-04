package com.example.agent.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LogDirectoryManagerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(LogDirectoryManagerTest.class);
    
    @Test
    void testLogDirectoriesCreation() {
        LogDirectoryManager.ensureDirectoriesExist();
        
        Path conversationDir = LogDirectoryManager.getConversationLogDir(LocalDate.now());
        Path metricsDir = LogDirectoryManager.getMetricsDir();
        
        assertTrue(Files.exists(conversationDir), "对话日志目录应该存在");
        assertTrue(Files.exists(metricsDir), "指标日志目录应该存在");
        
        logger.info("日志目录测试通过");
    }
    
    @Test
    void testConversationLogFilePath() {
        String conversationId = "test123";
        LocalDate date = LocalDate.now();
        
        Path logFile = LogDirectoryManager.getConversationLogFile(conversationId, date);
        
        assertTrue(logFile.toString().contains("conversations"), "路径应包含 conversations");
        assertTrue(logFile.toString().contains("conv_test123.log"), "文件名应正确");
        
        logger.debug("对话日志文件路径: {}", logFile);
    }
    
    @Test
    void testTokenMetricsFilePath() {
        LocalDate date = LocalDate.now();
        
        Path metricsFile = LogDirectoryManager.getTokenMetricsFile(date);
        
        assertTrue(metricsFile.toString().contains("metrics"), "路径应包含 metrics");
        assertTrue(metricsFile.toString().endsWith(".csv"), "文件应以 .csv 结尾");
        
        logger.debug("Token 指标文件路径: {}", metricsFile);
    }
    
    @Test
    void testLoggerFunctionality() {
        logger.trace("这是 TRACE 级别日志");
        logger.debug("这是 DEBUG 级别日志");
        logger.info("这是 INFO 级别日志");
        logger.warn("这是 WARN 级别日志");
        logger.error("这是 ERROR 级别日志");
        
        Path logFile = LogDirectoryManager.getLogRoot().resolve("agent.log");
        assertTrue(Files.exists(logFile), "日志文件应该存在");
    }
}
