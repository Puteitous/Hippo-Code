package com.example.agent.logging;

import com.example.agent.llm.model.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TokenMetricsCollectorTest {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenMetricsCollectorTest.class);
    
    private TokenMetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        collector = new TokenMetricsCollector(LocalDate.now());
    }
    
    @Test
    void testRecordConversation() {
        String conversationId = "conv_test_" + System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();
        int estimatedTokens = 500;
        
        Usage usage = new Usage();
        usage.setPromptTokens(520);
        usage.setCompletionTokens(180);
        usage.setTotalTokens(700);
        
        collector.recordConversation(conversationId, timestamp, estimatedTokens, usage);
        
        assertTrue(Files.exists(collector.getMetricsFile()), "指标文件应该存在");
        
        logger.info("记录对话指标测试通过");
    }
    
    @Test
    void testRecordMultipleConversations() {
        for (int i = 0; i < 3; i++) {
            String conversationId = "conv_" + i + "_" + System.currentTimeMillis();
            LocalDateTime timestamp = LocalDateTime.now();
            int estimatedTokens = 500 + i * 100;
            
            Usage usage = new Usage();
            usage.setPromptTokens(520 + i * 50);
            usage.setCompletionTokens(180 + i * 20);
            usage.setTotalTokens(700 + i * 70);
            
            collector.recordConversation(conversationId, timestamp, estimatedTokens, usage);
        }
        
        try {
            long lineCount = Files.lines(collector.getMetricsFile()).count();
            assertTrue(lineCount >= 4, "应该有至少 4 行（标题 + 3 条记录）");
        } catch (Exception e) {
            fail("读取指标文件失败: " + e.getMessage());
        }
        
        logger.info("记录多个对话指标测试通过");
    }
    
    @Test
    void testPrintDailySummary() {
        for (int i = 0; i < 2; i++) {
            String conversationId = "conv_summary_" + i + "_" + System.currentTimeMillis();
            LocalDateTime timestamp = LocalDateTime.now();
            int estimatedTokens = 500;
            
            Usage usage = new Usage();
            usage.setPromptTokens(520);
            usage.setCompletionTokens(180);
            usage.setTotalTokens(700);
            
            collector.recordConversation(conversationId, timestamp, estimatedTokens, usage);
        }
        
        collector.printDailySummary();
        
        logger.info("打印日报测试通过");
    }
    
    @Test
    void testNullUsage() {
        String conversationId = "conv_null_" + System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();
        int estimatedTokens = 500;
        
        collector.recordConversation(conversationId, timestamp, estimatedTokens, null);
        
        assertTrue(Files.exists(collector.getMetricsFile()), "指标文件应该存在");
        
        logger.info("空 Usage 测试通过");
    }
}
