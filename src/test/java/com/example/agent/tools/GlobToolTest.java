package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobToolTest {

    private GlobTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new GlobTool();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetName() {
        assertEquals("glob", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("glob"));
        assertTrue(description.contains("通配符"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("pattern"));
        assertTrue(schema.contains("path"));
        assertTrue(schema.contains("max_results"));
    }

    @Test
    void testFindJavaFiles() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.java");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Glob 搜索结果"));
        assertTrue(result.contains(".java"));
        assertTrue(result.contains("找到"));
    }

    @Test
    void testFindPomFiles() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/pom.xml");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("pom.xml"));
    }

    @Test
    void testFindInSpecificDirectory() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.java");
        args.put("path", "src/main/java");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains(".java"));
    }

    @Test
    void testFindWithMaxResults() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.java");
        args.put("max_results", 5);
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Glob 搜索结果"));
    }

    @Test
    void testFindNonExistentPattern() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.nonexistent");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("未找到匹配的文件"));
    }

    @Test
    void testMissingPatternParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testInvalidSearchPath() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.java");
        args.put("path", "/non/existent/path");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testSearchFilePathInsteadOfDirectory() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.java");
        args.put("path", "pom.xml");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testRequiresFileLock() {
        assertFalse(tool.requiresFileLock());
    }

    @Test
    void testGetAffectedPaths() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "**/*.java");
        args.put("path", "src");
        
        var paths = tool.getAffectedPaths(args);
        
        assertNotNull(paths);
        assertEquals(1, paths.size());
        assertEquals("src", paths.get(0));
    }

    @Test
    void testSimplePattern() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "*.md");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Glob 搜索结果"));
    }
}
