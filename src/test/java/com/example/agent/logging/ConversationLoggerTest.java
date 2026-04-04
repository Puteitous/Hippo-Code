package com.example.agent.logging;

import com.example.agent.llm.model.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ConversationLoggerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationLoggerTest.class);
    
    private ConversationLogger conversationLogger;
    private Path logFile;
    
    @BeforeEach
    void setUp() {
        String conversationId = "test_" + System.currentTimeMillis();
        logFile = LogDirectoryManager.getConversationLogFile(conversationId, LocalDate.now());
        conversationLogger = new ConversationLogger(conversationId, logFile);
    }
    
    @Test
    void testLogUserInput() {
        String input = "帮我查看下 pom.xml 文件";
        int estimatedTokens = 15;
        
        conversationLogger.logUserInput(input, estimatedTokens);
        
        assertTrue(Files.exists(logFile), "日志文件应该存在");
        assertEquals(estimatedTokens, conversationLogger.getTotalInputTokens());
        
        logger.info("用户输入日志测试通过");
    }
    
    @Test
    void testLogAiResponse() {
        String response = "这是你的 pom.xml 文件内容...";
        Usage usage = new Usage();
        usage.setPromptTokens(520);
        usage.setCompletionTokens(180);
        usage.setTotalTokens(700);
        
        conversationLogger.logAiResponse(response, usage);
        
        assertEquals(520, conversationLogger.getTotalInputTokens());
        assertEquals(180, conversationLogger.getTotalOutputTokens());
        
        logger.info("AI 响应日志测试通过");
    }
    
    @Test
    void testLogToolCall() {
        String toolName = "read_file";
        String args = "{\"path\": \"pom.xml\"}";
        String result = "<?xml version=\"1.0\"...";
        long duration = 45;
        boolean success = true;
        
        conversationLogger.logToolCall(toolName, args, result, duration, success);
        
        assertEquals(1, conversationLogger.getTotalToolCalls());
        
        logger.info("工具调用日志测试通过");
    }
    
    @Test
    void testLogSummary() {
        conversationLogger.logUserInput("测试输入", 10);
        
        Usage usage = new Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(150);
        conversationLogger.logAiResponse("测试响应", usage);
        
        conversationLogger.logToolCall("test_tool", "{}", "结果", 100, true);
        
        conversationLogger.logSummary();
        
        assertTrue(Files.exists(logFile), "日志文件应该存在");
        
        try {
            String content = Files.readString(logFile);
            assertTrue(content.contains("对话摘要"), "应该包含对话摘要");
            assertTrue(content.contains("总 Token: 160"), "应该包含正确的总 token 数");
            assertTrue(content.contains("工具调用次数: 1"), "应该包含正确的工具调用次数");
        } catch (Exception e) {
            fail("读取日志文件失败: " + e.getMessage());
        }
        
        logger.info("对话摘要测试通过");
    }
    
    @Test
    void testCompleteConversationFlow() {
        logger.info("开始完整对话流程测试");
        
        conversationLogger.logUserInput("帮我分析项目结构", 20);
        
        conversationLogger.logToolCall("list_directory", 
            "{\"path\": \".\", \"recursive\": true}", 
            "项目结构...", 
            120, 
            true);
        
        Usage usage = new Usage();
        usage.setPromptTokens(800);
        usage.setCompletionTokens(300);
        usage.setTotalTokens(1100);
        conversationLogger.logAiResponse("项目结构分析完成...", usage);
        
        conversationLogger.logSummary();
        
        assertEquals(820, conversationLogger.getTotalInputTokens());
        assertEquals(300, conversationLogger.getTotalOutputTokens());
        assertEquals(1, conversationLogger.getTotalToolCalls());
        
        logger.info("完整对话流程测试通过");
    }
}
