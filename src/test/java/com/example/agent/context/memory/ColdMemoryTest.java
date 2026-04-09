package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ColdMemory 测试
 */
class ColdMemoryTest {

    private ColdMemory coldMemory;
    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        coldMemory = new ColdMemory(tokenEstimator);
    }

    @Test
    void testColdMemoryCreation() {
        assertNotNull(coldMemory);
    }

    @Test
    void testRetrieve() {
        String userInput = "分析 AgentContext 类的实现";
        List<Message> messages = coldMemory.retrieve(userInput, 1000);

        assertNotNull(messages);
        assertTrue(messages.size() > 0);

        // 检查返回的消息
        for (Message message : messages) {
            assertEquals("tool", message.getRole());
            assertTrue(message.getContent().contains("文件: "));
            assertTrue(message.getContent().contains("相关性: "));
        }
    }

    @Test
    void testRetrieveDisabled() {
        // 创建配置，禁用 ColdMemory
        ContextConfig.ColdMemoryConfig config = new ContextConfig.ColdMemoryConfig();
        config.setEnabled(false);

        ColdMemory memory = new ColdMemory(tokenEstimator, config);

        List<Message> messages = memory.retrieve("测试查询", 1000);

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testRetrieveEmptyInput() {
        List<Message> messages = coldMemory.retrieve("", 1000);

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testRetrieveNullInput() {
        List<Message> messages = coldMemory.retrieve(null, 1000);

        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testRetrieveWithTokenLimit() {
        String userInput = "分析代码结构";
        List<Message> messages = coldMemory.retrieve(userInput, 10); // 很小的 token 限制

        // 应该返回空列表或非常少的结果
        assertNotNull(messages);
    }

    @Test
    void testCache() {
        String userInput = "测试缓存";

        // 第一次检索
        List<Message> messages1 = coldMemory.retrieve(userInput, 1000);
        int cacheSize1 = coldMemory.getCacheSize();
        assertTrue(cacheSize1 > 0);

        // 第二次检索（应该使用缓存）
        List<Message> messages2 = coldMemory.retrieve(userInput, 1000);
        int cacheSize2 = coldMemory.getCacheSize();
        assertEquals(cacheSize1, cacheSize2);

        // 验证两次结果相同
        assertEquals(messages1.size(), messages2.size());
    }

    @Test
    void testCleanupCache() {
        String userInput = "测试清理缓存";

        // 检索以填充缓存
        coldMemory.retrieve(userInput, 1000);
        int cacheSizeBefore = coldMemory.getCacheSize();
        assertTrue(cacheSizeBefore > 0);

        // 清理缓存
        coldMemory.cleanupCache();
        int cacheSizeAfter = coldMemory.getCacheSize();
        assertEquals(0, cacheSizeAfter);
    }

    @Test
    void testMaxResults() {
        ContextConfig.ColdMemoryConfig config = new ContextConfig.ColdMemoryConfig();
        config.setMaxResults(1); // 只返回 1 个结果

        ColdMemory memory = new ColdMemory(tokenEstimator, config);

        List<Message> messages = memory.retrieve("测试查询", 1000);
        assertNotNull(messages);
        assertEquals(1, messages.size());
    }

    @Test
    void testSearchResult() {
        ColdMemory.SearchResult result = new ColdMemory.SearchResult(
                "test/file.java",
                "public class Test {}",
                0.95
        );

        assertEquals("test/file.java", result.getFilePath());
        assertEquals("public class Test {}", result.getContent());
        assertEquals(0.95, result.getRelevanceScore());
    }
}
