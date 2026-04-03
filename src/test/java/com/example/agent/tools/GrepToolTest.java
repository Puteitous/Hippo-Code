package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrepToolTest {

    private GrepTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new GrepTool();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetName() {
        assertEquals("grep", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("搜索"));
        assertTrue(description.contains("正则"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("pattern"));
        assertTrue(schema.contains("path"));
        assertTrue(schema.contains("file_pattern"));
    }

    @Test
    void testSearchSimplePattern() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "public class");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
        assertTrue(result.contains("public class"));
    }

    @Test
    void testSearchWithFilePattern() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "import");
        args.put("file_pattern", "*.java");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
        assertTrue(result.contains("import"));
    }

    @Test
    void testSearchInSpecificDirectory() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "ToolExecutor");
        args.put("path", "src/main/java");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
    }

    @Test
    void testCaseSensitiveSearch() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "PUBLIC");
        args.put("case_sensitive", true);
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
    }

    @Test
    void testCaseInsensitiveSearch() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "PUBLIC");
        args.put("case_sensitive", false);
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
        assertTrue(result.contains("找到"));
    }

    @Test
    void testSearchWithMaxResults() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "import");
        args.put("max_results", 5);
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
    }

    @Test
    void testSearchNonExistentPattern() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");
        args.put("file_pattern", "*.md");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("未找到匹配的内容"));
    }

    @Test
    void testMissingPatternParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testInvalidRegexPattern() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "[invalid(regex");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testInvalidSearchPath() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "test");
        args.put("path", "/non/existent/path");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testSearchFilePathInsteadOfDirectory() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "test");
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
        args.put("pattern", "test");
        args.put("path", "src");
        
        var paths = tool.getAffectedPaths(args);
        
        assertNotNull(paths);
        assertEquals(1, paths.size());
        assertEquals("src", paths.get(0));
    }

    @Test
    void testSearchRegexPattern() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("pattern", "public\\s+class\\s+\\w+");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("Grep 搜索结果"));
    }
}
