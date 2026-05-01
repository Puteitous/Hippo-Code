package com.example.agent.tools;

import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryRetriever;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.MemoryToolSandbox;
import com.example.agent.memory.embedding.EmbeddingService;
import com.example.agent.domain.rule.HippoRulesParser;
import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证记忆工具引导有效性
 * 
 * 测试点：
 * 1. search_memory 工具描述包含明确的使用场景
 * 2. recall_memory 工具描述包含明确的使用场景
 * 3. 持久上下文块末尾包含 search_memory 引导语
 * 4. 工具注册到 ToolRegistry 后可以通过 toTools() 获取
 */
@DisplayName("记忆工具引导有效性测试")
class MemoryToolGuidanceTest {

    @TempDir
    Path tempDir;

    private MemoryStore store;
    private MemoryToolSandbox sandbox;
    private SmartMockEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        sandbox = new MemoryToolSandbox(tempDir);
        store = new MemoryStore(sandbox);
        embeddingService = new SmartMockEmbeddingService();
    }

    @Test
    @DisplayName("search_memory 工具描述包含使用场景")
    void testSearchMemoryToolDescription() {
        SearchMemoryTool tool = new SearchMemoryTool(store, embeddingService);
        String description = tool.getDescription();

        assertNotNull(description);
        assertTrue(description.contains("search_memory"), "工具描述应包含工具名称");
        assertTrue(description.contains("过去的工作") || description.contains("决策"),
            "工具描述应说明适用场景：过去的工作或决策");
        assertTrue(description.contains("项目特定") || description.contains("约束"),
            "工具描述应说明适用场景：项目约束");
        assertTrue(description.contains("recall_memory"),
            "工具描述应引导使用 recall_memory 获取完整内容");
    }

    @Test
    @DisplayName("recall_memory 工具描述包含使用场景")
    void testRecallMemoryToolDescription() {
        RecallMemoryTool tool = new RecallMemoryTool(store);
        String description = tool.getDescription();

        assertNotNull(description);
        assertTrue(description.contains("recall_memory"), "工具描述应包含工具名称");
        assertTrue(description.contains("search_memory"),
            "工具描述应说明在 search_memory 之后使用");
        assertTrue(description.contains("完整内容") || description.contains("详细信息"),
            "工具描述应说明返回完整内容");
    }

    @Test
    @DisplayName("持久上下文块末尾包含 search_memory 引导语")
    void testPersistentContextGuidance() {
        // 添加持久类型记忆
        MemoryEntry pref = new MemoryEntry("pref-1",
            "# 代码风格偏好\n\n使用 4 空格缩进，避免使用 tab。",
            MemoryEntry.MemoryType.USER_PREFERENCE, Set.of("style"), 0.9);
        pref.setEmbedding(embeddingService.embed(pref.getContent()));
        store.add(pref);

        MemoryRetriever retriever = new MemoryRetriever(store, new HippoRulesParser(), embeddingService);

        String persistentContext = retriever.injectPersistentContext();

        assertNotNull(persistentContext);
        assertTrue(persistentContext.contains("Persistent Context"),
            "应包含持久上下文标题");
        assertTrue(persistentContext.contains("search_memory"),
            "持久上下文块末尾应包含 search_memory 引导语");
        assertTrue(persistentContext.contains("natural language query"),
            "引导语应说明使用自然语言查询");
    }

    @Test
    @DisplayName("工具注册后可以通过 toTools() 获取")
    void testToolRegistration() {
        com.example.agent.tools.ToolRegistry registry = new com.example.agent.tools.ToolRegistry();
        registry.register(new SearchMemoryTool(store, embeddingService));
        registry.register(new RecallMemoryTool(store));

        assertTrue(registry.hasTool("search_memory"));
        assertTrue(registry.hasTool("recall_memory"));

        var tools = registry.toTools();
        assertEquals(2, tools.size());

        // 验证工具描述非空
        for (var tool : tools) {
            assertNotNull(tool.getFunction());
            assertNotNull(tool.getFunction().getDescription());
            assertFalse(tool.getFunction().getDescription().isEmpty(),
                "工具 " + tool.getFunction().getName() + " 的描述不应为空");
        }
    }

    @Test
    @DisplayName("search_memory 返回摘要不包含完整内容")
    void testSearchMemoryReturnsSummary() throws ToolExecutionException {
        String longContent = "# 数据库配置\n\n" +
            "我们使用了 HikariCP 连接池，配置如下：\n" +
            "maximumPoolSize: 20\n" +
            "minimumIdle: 5\n" +
            "connectionTimeout: 30000\n" +
            "idleTimeout: 600000\n" +
            "maxLifetime: 1800000\n\n" +
            "## 连接字符串\n\n" +
            "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC";

        MemoryEntry entry = new MemoryEntry("db-config",
            longContent,
            MemoryEntry.MemoryType.PROJECT_CONTEXT, Set.of("database", "config"), 0.9);
        entry.setEmbedding(embeddingService.embed(entry.getContent()));
        store.add(entry);

        SearchMemoryTool tool = new SearchMemoryTool(store, embeddingService);

        // 使用简洁查询，避免查询字符串本身包含测试内容
        String result = tool.execute(
            createJsonNode("{\"query\": \"数据库连接池配置\"}")
        );

        assertNotNull(result);
        assertTrue(result.contains("db-config"), "应包含记忆 ID");
        assertTrue(result.contains("Summary:"), "应包含摘要");
        // generateHook 只取第一行，所以不应包含后面的连接字符串
        assertFalse(result.contains("jdbc:mysql://"),
            "search_memory 摘要不应返回完整内容");
        assertTrue(result.contains("recall_memory"),
            "应引导使用 recall_memory 获取完整内容");
    }

    @Test
    @DisplayName("recall_memory 返回完整内容（带截断）")
    void testRecallMemoryReturnsFullContent() throws ToolExecutionException {
        String content = "# 项目决策\n\n使用 Spring Boot 3.0 作为基础框架。";
        MemoryEntry entry = new MemoryEntry("decision-1",
            content,
            MemoryEntry.MemoryType.DECISION, Set.of("spring"), 0.8);
        entry.setEmbedding(embeddingService.embed(entry.getContent()));
        store.add(entry);

        RecallMemoryTool tool = new RecallMemoryTool(store);

        String result = tool.execute(
            createJsonNode("{\"id\": \"decision-1\"}")
        );

        assertNotNull(result);
        assertTrue(result.contains("decision-1"), "应包含记忆 ID");
        assertTrue(result.contains("Spring Boot 3.0"), "应包含完整内容");
        assertTrue(result.contains("Type:"), "应包含元数据");
    }

    private com.fasterxml.jackson.databind.JsonNode createJsonNode(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 简单的 Mock EmbeddingService，生成固定向量（确保测试中所有记忆都能匹配）
     */
    static class SmartMockEmbeddingService implements EmbeddingService {
        @Override
        public float[] embed(String text) {
            // 返回固定向量，确保测试中所有记忆都能匹配查询
            float[] vec = new float[512];
            for (int i = 0; i < 512; i++) {
                vec[i] = 0.5f;
            }
            return vec;
        }

        @Override
        public float[][] embedBatch(String[] texts) {
            float[][] result = new float[texts.length][];
            for (int i = 0; i < texts.length; i++) {
                result[i] = embed(texts[i]);
            }
            return result;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public int getDimension() {
            return 512;
        }

        @Override
        public void close() {}
    }
}
