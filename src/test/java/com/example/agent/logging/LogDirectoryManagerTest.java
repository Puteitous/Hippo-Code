package com.example.agent.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LogDirectoryManagerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(LogDirectoryManagerTest.class);
    
    @TempDir
    Path tempDir;
    
    @Test
    void testLogDirectoriesCreation() {
        LogDirectoryManager.ensureDirectoriesExist();
        
        Path systemDir = LogDirectoryManager.getSystemLogDir();
        Path conversationDir = LogDirectoryManager.getConversationLogDir(LocalDate.now());
        Path metricsDir = LogDirectoryManager.getMetricsDir();
        
        assertTrue(Files.exists(systemDir), "系统日志目录应该存在");
        assertTrue(Files.exists(conversationDir), "对话日志目录应该存在");
        assertTrue(Files.exists(metricsDir), "指标日志目录应该存在");
        
        logger.info("日志目录测试通过");
    }
    
    @Test
    void testSystemLogDir() {
        Path systemDir = LogDirectoryManager.getSystemLogDir();
        
        assertTrue(systemDir.toString().contains("system"), "路径应包含 system");
        assertTrue(systemDir.endsWith("logs/system"), "系统日志目录路径应正确");
        
        logger.debug("系统日志目录路径: {}", systemDir);
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
        logger.trace("[测试] TRACE 级别日志");
        logger.debug("[测试] DEBUG 级别日志");
        logger.info("[测试] INFO 级别日志");
        logger.warn("[测试] WARN 级别日志");
        logger.error("[测试] ERROR 级别日志");
        
        Path systemDir = LogDirectoryManager.getSystemLogDir();
        
        logger.info("日志文件应该在: {}", systemDir.resolve("agent.log"));
    }
}
