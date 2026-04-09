package com.example.agent.tools;

import com.example.agent.context.memory.ColdMemory;
import com.example.agent.service.SimpleTokenEstimator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchCodeTool 测试
 * 
 * 边界条件测试重点：
 * - null/空值参数
 * - 负数/零值参数
 * - 参数边界
 * - ColdMemory集成
 */
class SearchCodeToolTest {

    private SearchCodeTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new SearchCodeTool();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetName() {
        assertEquals("search_code", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("检索"));
        assertTrue(description.contains("代码"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("query"));
    }

    @Test
    void testMissingQueryParameter() {
        ObjectNode args = objectMapper.createObjectNode();

        assertThrows(ToolExecutionException.class, () -> tool.execute(args));
    }

    @Test
    void testNullQueryParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.putNull("query");

        assertThrows(ToolExecutionException.class, () -> tool.execute(args));
    }

    @Test
    void testEmptyQueryParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "");

        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> tool.execute(args));
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testWhitespaceQueryParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "   ");

        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> tool.execute(args));
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testNormalSearchWithoutColdMemory() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");

        String result = tool.execute(args);
        assertNotNull(result);
        assertTrue(result.contains("检索功能暂不可用"));
    }

    @Test
    void testNormalSearchWithColdMemory() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");

        String result = tool.execute(args);
        assertNotNull(result);
        assertTrue(result.contains("代码库检索结果"));
    }

    @Test
    void testSearchWithZeroMaxResults() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_results", 0);

        String result = tool.execute(args);
        assertNotNull(result);
        assertTrue(result.contains("未找到相关代码文件") || result.contains("[]"));
    }

    @Test
    void testSearchWithNegativeMaxResults() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_results", -5);

        String result = tool.execute(args);
        assertNotNull(result);
    }

    @Test
    void testSearchWithVeryLargeMaxResults() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_results", 1000000);

        String result = tool.execute(args);
        assertNotNull(result);
    }

    @Test
    void testSearchWithZeroMaxTokens() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_tokens", 0);

        String result = tool.execute(args);
        assertNotNull(result);
        assertTrue(result.contains("未找到相关代码文件"));
    }

    @Test
    void testSearchWithNegativeMaxTokens() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_tokens", -100);

        String result = tool.execute(args);
        assertNotNull(result);
        assertTrue(result.contains("未找到相关代码文件"));
    }

    @Test
    void testSearchWithVerySmallMaxTokens() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_tokens", 10);

        String result = tool.execute(args);
        assertNotNull(result);
    }

    @Test
    void testSearchWithVeryLargeMaxTokens() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");
        args.put("max_tokens", Integer.MAX_VALUE);

        String result = tool.execute(args);
        assertNotNull(result);
    }

    @Test
    void testSetColdMemory() {
        assertDoesNotThrow(() -> tool.setColdMemory(null));
        assertDoesNotThrow(() -> tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator())));
    }

    @Test
    void testToolMetadata() {
        assertFalse(tool.requiresFileLock());
        assertTrue(tool.getAffectedPaths(objectMapper.createObjectNode()).isEmpty());
    }

    @Test
    void testAffectedPathsWithQuery() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");

        assertTrue(tool.getAffectedPaths(args).isEmpty());
    }

    @Test
    void testNullColdMemoryGracefulDegradation() throws Exception {
        tool.setColdMemory(null);

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "AgentContext");

        String result = tool.execute(args);
        assertNotNull(result);
        assertTrue(result.contains("检索功能暂不可用"));
        assertTrue(result.contains("glob"));
        assertTrue(result.contains("grep"));
    }

    @Test
    void testSearchWithSpecialCharacters() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "类 方法 变量 @ # $ % ^ & * ()");

        String result = tool.execute(args);
        assertNotNull(result);
    }

    @Test
    void testSearchWithUnicodeQuery() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "你好世界 🎉 日本語");

        String result = tool.execute(args);
        assertNotNull(result);
    }

    @Test
    void testVeryLongQuery() throws Exception {
        tool.setColdMemory(new ColdMemory(new SimpleTokenEstimator()));

        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longQuery.append("AgentContext ");
        }

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", longQuery.toString());

        String result = tool.execute(args);
        assertNotNull(result);
    }
}
