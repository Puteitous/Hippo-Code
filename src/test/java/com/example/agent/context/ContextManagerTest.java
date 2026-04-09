package com.example.agent.context;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextManager 集成测试
 */
class ContextManagerTest {

    private ContextManager contextManager;
    private TokenEstimator tokenEstimator;
    private ContextConfig config;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        config = new ContextConfig();
        contextManager = new ContextManager(tokenEstimator, config);
    }

    @Test
    void testContextManagerCreation() {
        assertNotNull(contextManager);
        assertNotNull(contextManager.getHotMemory());
        assertNotNull(contextManager.getWarmMemory());
        assertNotNull(contextManager.getColdMemory());
        assertNotNull(contextManager.getMetrics());
    }

    @Test
    void testEnhanceSystemPrompt() {
        String basePrompt = "你是一个AI助手。";
        String enhancedPrompt = contextManager.enhanceSystemPrompt(basePrompt);

        assertNotNull(enhancedPrompt);
        assertTrue(enhancedPrompt.contains(basePrompt));
    }

    @Test
    void testProcessUserInput() {
        String userInput = "请分析 @src/main/java/Test.java 文件";
        List<Message> messages = contextManager.processUserInput(userInput);

        assertNotNull(messages);
        // 由于文件可能不存在，消息列表可能为空
    }

    @Test
    void testProcessUserInputEmpty() {
        List<Message> messages = contextManager.processUserInput("");

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testProcessUserInputNull() {
        List<Message> messages = contextManager.processUserInput(null);

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testGetStatusReport() {
        String report = contextManager.getStatusReport();

        assertNotNull(report);
        assertTrue(report.contains("三层记忆状态报告"));
        assertTrue(report.contains("HotMemory"));
        assertTrue(report.contains("WarmMemory"));
        assertTrue(report.contains("ColdMemory"));
        assertTrue(report.contains("性能指标"));
    }

    @Test
    void testCleanup() {
        // 先处理一些输入以填充缓存
        contextManager.processUserInput("测试查询");

        // 清理缓存
        contextManager.cleanup();

        // 验证缓存已清理
        assertEquals(0, contextManager.getWarmMemory().getCacheSize());
        assertEquals(0, contextManager.getColdMemory().getCacheSize());
    }

    @Test
    void testMetricsRecording() {
        // 记录一些请求
        contextManager.processUserInput("查询1");
        contextManager.processUserInput("查询2");
        contextManager.processUserInput("查询3");

        ContextManager.MemoryMetrics metrics = contextManager.getMetrics();
        String report = metrics.getReport();

        assertNotNull(report);
        assertTrue(report.contains("请求次数"));
    }

    @Test
    void testWithCustomConfig() {
        // 创建自定义配置
        ContextConfig customConfig = new ContextConfig();
        customConfig.getWarmMemory().setAtReferenceEnabled(false);
        customConfig.getColdMemory().setEnabled(false);

        ContextManager customManager = new ContextManager(tokenEstimator, customConfig);

        List<Message> messages = customManager.processUserInput("测试");
        assertNotNull(messages);
        assertTrue(messages.isEmpty()); // 禁用后应该没有消息
    }
}
