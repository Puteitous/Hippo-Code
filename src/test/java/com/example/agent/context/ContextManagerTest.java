package com.example.agent.context;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextManager 集成测试
 * 
 * 新架构：职责简化为三层记忆管理
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
    }

    @Test
    void testInitialize() {
        assertDoesNotThrow(() -> contextManager.initialize());
    }

    @Test
    void testEnhanceSystemPrompt() {
        String basePrompt = "你是一个AI助手。";
        String enhancedPrompt = contextManager.enhanceSystemPrompt(basePrompt);

        assertNotNull(enhancedPrompt);
        assertTrue(enhancedPrompt.contains(basePrompt));
    }

    @Test
    void testGetStatusReport() {
        String report = contextManager.getStatusReport();

        assertNotNull(report);
        assertTrue(report.contains("三层记忆状态报告"));
        assertTrue(report.contains("HotMemory"));
        assertTrue(report.contains("WarmMemory"));
        assertTrue(report.contains("ColdMemory"));
    }

    @Test
    void testCleanup() {
        // cleanup() 调用各层记忆的 cleanupCache()，只清理过期项
        // 调用 clearCache() 清空所有缓存后，cleanup() 应该不会出错
        contextManager.getWarmMemory().clearCache();
        contextManager.getColdMemory().clearCache();

        assertDoesNotThrow(() -> contextManager.cleanup());

        assertEquals(0, contextManager.getWarmMemory().getCacheSize());
        assertEquals(0, contextManager.getColdMemory().getCacheSize());
    }

    @Test
    void testCleanupWithEmptyCache() {
        // 确保缓存为空
        contextManager.getWarmMemory().clearCache();
        contextManager.getColdMemory().clearCache();

        // 清理空缓存不应该抛出异常
        assertDoesNotThrow(() -> contextManager.cleanup());
    }

    @Test
    void testMemoryAccessors() {
        assertNotNull(contextManager.getHotMemory());
        assertNotNull(contextManager.getWarmMemory());
        assertNotNull(contextManager.getColdMemory());
    }

    @Test
    void testStatusReportFormat() {
        String report = contextManager.getStatusReport();

        assertNotNull(report);
        assertFalse(report.isEmpty());
        assertTrue(report.contains("设计理念"));
        assertTrue(report.contains("LLM 自主决策"));
    }

    @Test
    void testNullTokenEstimator() {
        // ContextManager 应该处理 tokenEstimator 为 null 的情况
        // 或抛出合适的异常
        assertDoesNotThrow(() -> new ContextManager(null, config));
    }

    @Test
    void testNullConfig() {
        // ContextManager 应该处理 config 为 null 的情况
        assertDoesNotThrow(() -> new ContextManager(tokenEstimator, null));
    }

    @Test
    void testMultipleInitializationCalls() {
        assertDoesNotThrow(() -> {
            contextManager.initialize();
            contextManager.initialize();
            contextManager.initialize();
        });
    }

    @Test
    void testEnhanceSystemPromptWithNull() {
        String result = contextManager.enhanceSystemPrompt(null);
        // 应该返回 null 或处理为安全值
        assertNotNull(result);
    }

    @Test
    void testEnhanceSystemPromptWithEmptyString() {
        String result = contextManager.enhanceSystemPrompt("");
        assertNotNull(result);
    }
}
