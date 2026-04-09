package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WarmMemory 测试
 * 
 * 边界条件测试重点：
 * - null/空值参数
 * - 负数/零值参数
 * - 文件不存在/权限问题
 * - 语言识别失败降级
 * - 缓存边界
 */
class WarmMemoryTest {

    private WarmMemory warmMemory;
    private TokenEstimator tokenEstimator;
    private static final String TEST_FILE_CONTENT = "public class TestClass {\n" +
            "    public void testMethod() {\n" +
            "        System.out.println(\"Hello World\");\n" +
            "    }\n" +
            "}";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        warmMemory = new WarmMemory(tokenEstimator);
    }

    @Test
    void testWarmMemoryCreation() {
        assertNotNull(warmMemory);
        assertEquals(0, warmMemory.getCacheSize());
    }

    @Test
    void testWarmMemoryWithNullConfig() {
        WarmMemory memory = new WarmMemory(tokenEstimator, null);
        assertNotNull(memory);
    }

    @Test
    void testReadFileNormalCase() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        String content = warmMemory.readFile(testFile.toString());
        assertNotNull(content);
        assertTrue(content.contains("public class TestClass"));
        assertEquals(1, warmMemory.getCacheSize());
    }

    @Test
    void testReadFileWithNullPath() {
        String content = warmMemory.readFile(null);
        assertNull(content);
    }

    @Test
    void testReadFileWithEmptyPath() {
        String content = warmMemory.readFile("");
        assertNull(content);
    }

    @Test
    void testReadFileWithBlankPath() {
        String content = warmMemory.readFile("   ");
        assertNull(content);
    }

    @Test
    void testReadNonExistentFile() {
        String content = warmMemory.readFile("/non/existent/file/12345.java");
        assertNull(content);
        assertEquals(0, warmMemory.getCacheSize());
    }

    @Test
    void testReadDirectoryInsteadOfFile() {
        String content = warmMemory.readFile(tempDir.toString());
        assertNull(content);
    }

    @Test
    void testReadFileWithZeroMaxTokens() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        String content = warmMemory.readFile(testFile.toString(), 0);
        assertNotNull(content);
    }

    @Test
    void testReadFileWithNegativeMaxTokens() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        String content = warmMemory.readFile(testFile.toString(), -100);
        assertNotNull(content);
    }

    @Test
    void testReadFileWithVerySmallMaxTokens() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        // 使用足够小的 token 值，使用 txt 后缀确保走 simpleTruncate 路径
        Path txtFile = tempDir.resolve("test.txt");
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("Line ").append(i).append("\n");
        }
        Files.writeString(txtFile, largeContent.toString());

        String content = warmMemory.readFile(txtFile.toString(), 10);
        assertNotNull(content);

        // 验证内容被截断（比原始内容短）
        assertTrue(content.length() < largeContent.length(), 
            "内容应该被截断: " + content.length() + " >= " + largeContent.length());
    }

    @Test
    void testReadFileTwiceUsesCache() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        String content1 = warmMemory.readFile(testFile.toString());
        assertEquals(1, warmMemory.getCacheSize());

        String content2 = warmMemory.readFile(testFile.toString());
        assertEquals(1, warmMemory.getCacheSize());

        assertNotNull(content1);
        assertNotNull(content2);
        assertEquals(content1, content2);
    }

    @Test
    void testReadUnknownFileType() throws IOException {
        Path testFile = tempDir.resolve("file.unknown123");
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("This is line ").append(i).append("\n");
        }
        Files.writeString(testFile, largeContent.toString());

        // 使用小 token，强制触发截断
        String content = warmMemory.readFile(testFile.toString(), 10);
        assertNotNull(content);

        // 验证内容被截断
        assertTrue(content.length() < largeContent.length());
    }

    @Test
    void testReadPythonFile() throws IOException {
        Path testFile = tempDir.resolve("test.py");
        Files.writeString(testFile, "def test():\n    print('hello')\n    x = 1\n    y = 2\n    z = 3");

        String content = warmMemory.readFile(testFile.toString());
        assertNotNull(content);
    }

    @Test
    void testReadJavaScriptFile() throws IOException {
        Path testFile = tempDir.resolve("test.js");
        Files.writeString(testFile, "function test() {\n    console.log('hello');\n}");

        String content = warmMemory.readFile(testFile.toString());
        assertNotNull(content);
    }

    @Test
    void testCleanupCacheOnlyRemovesExpired() throws IOException {
        Path testFile1 = tempDir.resolve("Test1.java");
        Path testFile2 = tempDir.resolve("Test2.java");
        Files.writeString(testFile1, TEST_FILE_CONTENT);
        Files.writeString(testFile2, TEST_FILE_CONTENT);

        warmMemory.readFile(testFile1.toString());
        warmMemory.readFile(testFile2.toString());
        int cacheSizeBefore = warmMemory.getCacheSize();
        assertEquals(2, cacheSizeBefore);

        // cleanupCache 只清理过期缓存，新缓存不应被清理
        warmMemory.cleanupCache();
        assertEquals(cacheSizeBefore, warmMemory.getCacheSize());
    }

    @Test
    void testClearCache() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        warmMemory.readFile(testFile.toString());
        assertEquals(1, warmMemory.getCacheSize());

        warmMemory.clearCache();
        assertEquals(0, warmMemory.getCacheSize());
    }

    @Test
    void testCleanupEmptyCache() {
        assertEquals(0, warmMemory.getCacheSize());
        assertDoesNotThrow(() -> warmMemory.cleanupCache());
        assertEquals(0, warmMemory.getCacheSize());
    }

    @Test
    void testClearEmptyCache() {
        assertEquals(0, warmMemory.getCacheSize());
        assertDoesNotThrow(() -> warmMemory.clearCache());
        assertEquals(0, warmMemory.getCacheSize());
    }

    @Test
    void testReadFileWithNullTokenEstimator() {
        assertDoesNotThrow(() -> new WarmMemory(null));
    }

    @Test
    void testReadEmptyFile() throws IOException {
        Path testFile = tempDir.resolve("Empty.java");
        Files.writeString(testFile, "");

        String content = warmMemory.readFile(testFile.toString());
        assertNotNull(content);
        assertTrue(content.isEmpty());
    }

    @Test
    void testReadVeryLargeFile() throws IOException {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("public class Test").append(i).append(" {}\n");
        }

        Path testFile = tempDir.resolve("Large.java");
        Files.writeString(testFile, largeContent.toString());

        String content = warmMemory.readFile(testFile.toString(), 100);
        assertNotNull(content);
    }

    @Test
    void testReadFileWithCustomConfig() throws IOException {
        ContextConfig.WarmMemoryConfig config = new ContextConfig.WarmMemoryConfig();
        config.setMaxFileTokens(50);

        WarmMemory memory = new WarmMemory(tokenEstimator, config);

        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        String content = memory.readFile(testFile.toString());
        assertNotNull(content);
    }

    @Test
    void testMultipleReadsFromCache() throws IOException {
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        for (int i = 0; i < 10; i++) {
            String content = warmMemory.readFile(testFile.toString());
            assertNotNull(content);
        }

        assertEquals(1, warmMemory.getCacheSize());
    }

    @Test
    void testReadDifferentFileTypes() throws IOException {
        String[] extensions = {"java", "py", "js", "ts", "txt", "md", "json", "xml"};

        for (String ext : extensions) {
            Path testFile = tempDir.resolve("test." + ext);
            Files.writeString(testFile, "test content");
            String content = warmMemory.readFile(testFile.toString());
            assertNotNull(content);
        }

        assertEquals(extensions.length, warmMemory.getCacheSize());
    }

    @Test
    void testReadFileWithSpecialCharactersInPath() throws IOException {
        Path testFile = tempDir.resolve("文件 with spaces 和中文.java");
        Files.writeString(testFile, TEST_FILE_CONTENT);

        String content = warmMemory.readFile(testFile.toString());
        assertNotNull(content);
    }
}
