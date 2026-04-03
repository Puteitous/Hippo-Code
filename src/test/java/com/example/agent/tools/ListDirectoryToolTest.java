package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListDirectoryToolTest {

    private ListDirectoryTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new ListDirectoryTool();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetName() {
        assertEquals("list_directory", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("目录"));
        assertTrue(description.contains("递归"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("path"));
        assertTrue(schema.contains("recursive"));
        assertTrue(schema.contains("max_depth"));
    }

    @Test
    void testListCurrentDirectory() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", ".");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("目录内容"));
        assertTrue(result.contains("src"));
        assertTrue(result.contains("pom.xml"));
    }

    @Test
    void testListRecursive() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", ".");
        args.put("recursive", true);
        args.put("max_depth", 2);
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("目录树"));
        assertTrue(result.contains("src/"));
        assertTrue(result.contains("main/"));
    }

    @Test
    void testListNonExistentDirectory() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", "/non/existent/path");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testListFileInsteadOfDirectory() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
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
        args.put("path", "src");
        
        var paths = tool.getAffectedPaths(args);
        
        assertNotNull(paths);
        assertEquals(1, paths.size());
        assertEquals("src", paths.get(0));
    }
}
