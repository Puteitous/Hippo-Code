package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

class UndoFileToolTest {

    private UndoFileTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new UndoFileTool();
        objectMapper = new ObjectMapper();
        FileChangeTracker.resetForTest();
    }

    @Test
    void testGetName() {
        assertEquals("undo_file", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("撤销"));
        assertTrue(description.contains("edit_file"));
        assertTrue(description.contains("write_file"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("path"));
    }

    @Test
    void testMissingPathParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("old_text", "test");
        args.put("new_text", "replacement");

        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> tool.execute(args));
        assertTrue(exception.getMessage().contains("缺少必需参数"));
    }

    @Test
    void testEmptyPathParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("path", "");

        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> tool.execute(args));
        assertTrue(exception.getMessage().contains("path 参数不能为空"));
    }

    @Test
    void testUndoNonExistentFile() {
        try (MockedStatic<PathSecurityUtils> securityUtilsMock = mockStatic(PathSecurityUtils.class)) {
            securityUtilsMock.when(() -> PathSecurityUtils.validateAndResolve(anyString()))
                .thenReturn(Path.of("/nonexistent/file.txt"));

            ObjectNode args = objectMapper.createObjectNode();
            args.put("path", "/nonexistent/file.txt");

            ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> tool.execute(args));
            assertTrue(exception.getMessage().contains("文件不存在"));
        }
    }

    @Test
    void testUndoWithNoChanges(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("no_changes.txt");
        Files.writeString(testFile, "content", StandardCharsets.UTF_8);

        FileChangeTracker.setStorageDirForTest(tempDir);

        try (MockedStatic<PathSecurityUtils> securityUtilsMock = mockStatic(PathSecurityUtils.class)) {
            securityUtilsMock.when(() -> PathSecurityUtils.validateAndResolve(anyString()))
                .thenReturn(testFile);

            ObjectNode args = objectMapper.createObjectNode();
            args.put("path", testFile.toString());

            String result = tool.execute(args);
            assertTrue(result.contains("没有可撤销的变更记录"));
        }
    }

    @Test
    void testUndoSingleChange(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("single_undo.txt");
        Files.writeString(testFile, "original content", StandardCharsets.UTF_8);

        FileChangeTracker.setStorageDirForTest(tempDir);
        FileChangeTracker.recordChange(
            testFile.toAbsolutePath().toString(),
            "original content",
            "modified content",
            "edit_file"
        );

        try (MockedStatic<PathSecurityUtils> securityUtilsMock = mockStatic(PathSecurityUtils.class)) {
            securityUtilsMock.when(() -> PathSecurityUtils.validateAndResolve(anyString()))
                .thenReturn(testFile);
            securityUtilsMock.when(() -> PathSecurityUtils.getRelativePath(any()))
                .thenReturn("single_undo.txt");

            ObjectNode args = objectMapper.createObjectNode();
            args.put("path", testFile.toString());

            String result = tool.execute(args);
            assertTrue(result.contains("已撤销"));

            String content = Files.readString(testFile, StandardCharsets.UTF_8);
            assertEquals("original content", content);
        }
    }

    @Test
    void testUndoMultipleTimes(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("multi_undo.txt");
        Files.writeString(testFile, "v0", StandardCharsets.UTF_8);

        FileChangeTracker.setStorageDirForTest(tempDir);
        FileChangeTracker.recordChange(testFile.toAbsolutePath().toString(), "v0", "v1", "edit_file");
        FileChangeTracker.recordChange(testFile.toAbsolutePath().toString(), "v1", "v2", "edit_file");

        try (MockedStatic<PathSecurityUtils> securityUtilsMock = mockStatic(PathSecurityUtils.class)) {
            securityUtilsMock.when(() -> PathSecurityUtils.validateAndResolve(anyString()))
                .thenReturn(testFile);
            securityUtilsMock.when(() -> PathSecurityUtils.getRelativePath(any()))
                .thenReturn("multi_undo.txt");

            ObjectNode args = objectMapper.createObjectNode();
            args.put("path", testFile.toString());

            tool.execute(args);
            assertEquals("v1", Files.readString(testFile, StandardCharsets.UTF_8));

            tool.execute(args);
            assertEquals("v0", Files.readString(testFile, StandardCharsets.UTF_8));

            String result = tool.execute(args);
            assertTrue(result.contains("没有可撤销的变更记录"));
        }
    }

    @Test
    void testUndoCrlfPreservation(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("crlf_undo.txt");
        Files.writeString(testFile, "line1\r\nline2\r\nline3\r\n", StandardCharsets.UTF_8);

        FileChangeTracker.setStorageDirForTest(tempDir);
        FileChangeTracker.recordChange(
            testFile.toAbsolutePath().toString(),
            "line1\nline2\nline3\n",
            "modified\ncontent\n",
            "edit_file"
        );

        Files.writeString(testFile, "modified\r\ncontent\r\n", StandardCharsets.UTF_8);

        try (MockedStatic<PathSecurityUtils> securityUtilsMock = mockStatic(PathSecurityUtils.class)) {
            securityUtilsMock.when(() -> PathSecurityUtils.validateAndResolve(anyString()))
                .thenReturn(testFile);
            securityUtilsMock.when(() -> PathSecurityUtils.getRelativePath(any()))
                .thenReturn("crlf_undo.txt");

            ObjectNode args = objectMapper.createObjectNode();
            args.put("path", testFile.toString());

            String result = tool.execute(args);
            assertTrue(result.contains("已撤销"));

            String content = Files.readString(testFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("\r\n"), "回滚后应保留 CRLF 行尾");
            assertTrue(content.contains("line1"), "应包含原始内容");
        }
    }
}
