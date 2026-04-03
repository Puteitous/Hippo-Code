package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class EditFileToolTest {

    private EditFileTool tool;
    private ObjectMapper objectMapper;
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        tool = new EditFileTool();
        objectMapper = new ObjectMapper();
        testDir = Paths.get("test_temp_" + System.currentTimeMillis());
        Files.createDirectories(testDir);
    }

    @AfterEach
    void tearDown() {
        cleanupDirectory(testDir);
    }
    
    private void cleanupDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        
        try {
            Files.walk(dir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("警告: 清理测试文件失败: " + path + " - " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("警告: 清理测试目录失败: " + dir + " - " + e.getMessage());
        }
    }

    @Test
    void testGetName() {
        assertEquals("edit_file", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("替换"));
        assertTrue(description.contains("唯一匹配"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("path"));
        assertTrue(schema.contains("old_text"));
        assertTrue(schema.contains("new_text"));
    }

    @Test
    void testEditSingleLine() throws Exception {
        Path testFile = createTestFile("Hello World\nThis is a test\nGoodbye World");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "This is a test");
        args.put("new_text", "This is modified");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("文件编辑成功"));
        
        String content = Files.readString(testFile);
        assertEquals("Hello World\nThis is modified\nGoodbye World", content);
    }

    @Test
    void testEditMultiLine() throws Exception {
        Path testFile = createTestFile("Line 1\nLine 2\nLine 3");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "Line 1\nLine 2");
        args.put("new_text", "Modified Line 1\nModified Line 2");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("文件编辑成功"));
        
        String content = Files.readString(testFile);
        assertEquals("Modified Line 1\nModified Line 2\nLine 3", content);
    }

    @Test
    void testEditTextNotFound() throws Exception {
        Path testFile = createTestFile("Hello World");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "Not Found");
        args.put("new_text", "Replacement");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testEditMultipleMatches() throws Exception {
        Path testFile = createTestFile("Hello World\nHello World\nHello World");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "Hello World");
        args.put("new_text", "Hi");
        
        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
        
        assertTrue(exception.getMessage().contains("3 次"));
        assertTrue(exception.getMessage().contains("必须唯一匹配"));
    }

    @Test
    void testMissingPathParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("old_text", "test");
        args.put("new_text", "replacement");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testMissingOldTextParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", "test.txt");
        args.put("new_text", "replacement");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testMissingNewTextParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", "test.txt");
        args.put("old_text", "test");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testEditNonExistentFile() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", "/non/existent/file.txt");
        args.put("old_text", "test");
        args.put("new_text", "replacement");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testEditWithEmptyOldText() throws Exception {
        Path testFile = createTestFile("Hello World");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "");
        args.put("new_text", "Insert");
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testEditWithEmptyNewText() throws Exception {
        Path testFile = createTestFile("Hello World");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "Hello");
        args.put("new_text", "");
        
        String result = tool.execute(args);
        
        assertNotNull(result);
        assertTrue(result.contains("文件编辑成功"));
        
        String content = Files.readString(testFile);
        assertEquals(" World", content);
    }

    @Test
    void testRequiresFileLock() {
        assertTrue(tool.requiresFileLock());
    }

    @Test
    void testGetAffectedPaths() throws Exception {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", "test.txt");
        args.put("old_text", "test");
        args.put("new_text", "replacement");
        
        var paths = tool.getAffectedPaths(args);
        
        assertNotNull(paths);
        assertEquals(1, paths.size());
        assertEquals("test.txt", paths.get(0));
    }

    @Test
    void testEditPreservesOtherContent() throws Exception {
        Path testFile = createTestFile("Line 1\nLine 2\nLine 3\nLine 4\nLine 5");
        
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", testFile.toString());
        args.put("old_text", "Line 3");
        args.put("new_text", "Modified Line 3");
        
        tool.execute(args);
        
        String content = Files.readString(testFile);
        assertEquals("Line 1\nLine 2\nModified Line 3\nLine 4\nLine 5", content);
    }

    private Path createTestFile(String content) throws IOException {
        Path file = testDir.resolve("test_" + System.nanoTime() + ".txt");
        Files.writeString(file, content);
        return file;
    }
}
