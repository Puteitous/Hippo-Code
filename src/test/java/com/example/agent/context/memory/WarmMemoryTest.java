package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WarmMemory 测试
 */
class WarmMemoryTest {

    private WarmMemory warmMemory;
    private TokenEstimator tokenEstimator;
    private static final String TEST_FILE_CONTENT = "public class TestClass {\n    public void testMethod() {\n        System.out.println(\"Hello World\");\n    }\n}";
    private static final String TEST_INPUT_WITH_REFERENCE = "请分析 @test/TestClass.java 文件的内容";

    private Path tempTestFile;

    @BeforeEach
    void setUp() throws IOException {
        tokenEstimator = new SimpleTokenEstimator();

        // 创建临时目录和文件
        Path tempDir = Files.createTempDirectory("warm_memory_test");
        tempTestFile = tempDir.resolve("TestClass.java");
        Files.writeString(tempTestFile, TEST_FILE_CONTENT);
    }

    @Test
    void testWarmMemoryCreation() {
        WarmMemory memory = new WarmMemory(tokenEstimator);
        assertNotNull(memory);
    }

    @Test
    void testProcessReferences() throws IOException {
        // 创建配置
        ContextConfig.WarmMemoryConfig config = new ContextConfig.WarmMemoryConfig();
        config.setAtReferenceEnabled(true);

        WarmMemory memory = new WarmMemory(tokenEstimator, config);

        // 测试输入包含 @ 引用
        String input = "分析 @" + tempTestFile.toString();
        List<Message> messages = memory.processReferences(input);

        // 应该返回包含文件内容的消息
        assertNotNull(messages);
        assertEquals(1, messages.size());

        Message message = messages.get(0);
        assertEquals("tool", message.getRole());
        assertTrue(message.getContent().contains(TEST_FILE_CONTENT));
        assertTrue(message.getContent().contains(tempTestFile.toString()));
    }

    @Test
    void testProcessReferencesDisabled() {
        // 创建配置，禁用 @ 引用
        ContextConfig.WarmMemoryConfig config = new ContextConfig.WarmMemoryConfig();
        config.setAtReferenceEnabled(false);

        WarmMemory memory = new WarmMemory(tokenEstimator, config);

        List<Message> messages = memory.processReferences(TEST_INPUT_WITH_REFERENCE);

        // 应该返回空列表
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testProcessReferencesEmptyInput() {
        WarmMemory memory = new WarmMemory(tokenEstimator);

        List<Message> messages = memory.processReferences("");

        // 应该返回空列表
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testProcessReferencesNullInput() {
        WarmMemory memory = new WarmMemory(tokenEstimator);

        List<Message> messages = memory.processReferences(null);

        // 应该返回空列表
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testProcessReferencesNoReferences() {
        WarmMemory memory = new WarmMemory(tokenEstimator);

        List<Message> messages = memory.processReferences("普通输入，没有 @ 引用");

        // 应该返回空列表
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testReferenceLimit() throws IOException {
        // 创建配置，设置引用限制为 1
        ContextConfig.WarmMemoryConfig config = new ContextConfig.WarmMemoryConfig();
        config.setAtReferenceEnabled(true);
        config.setMaxRefsPerMessage(1);

        WarmMemory memory = new WarmMemory(tokenEstimator, config);

        // 测试输入包含多个 @ 引用
        String input = "分析 @" + tempTestFile.toString() + " 和 @another/file.java";
        List<Message> messages = memory.processReferences(input);

        // 应该只处理第一个引用
        assertNotNull(messages);
        assertEquals(1, messages.size());
    }

    @Test
    void testNonExistentFile() {
        WarmMemory memory = new WarmMemory(tokenEstimator);

        // 测试不存在的文件
        String input = "分析 @non_existent_file.java";
        List<Message> messages = memory.processReferences(input);

        // 应该返回空列表（文件不存在）
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    void testCacheCleanup() throws IOException, InterruptedException {
        // 创建配置，设置缓存时间为 1 秒
        ContextConfig.WarmMemoryConfig config = new ContextConfig.WarmMemoryConfig();
        config.setAtReferenceEnabled(true);
        config.setCacheTtlSeconds(1);

        WarmMemory memory = new WarmMemory(tokenEstimator, config);

        // 第一次加载文件
        String input = "分析 @" + tempTestFile.toString();
        List<Message> messages1 = memory.processReferences(input);
        assertEquals(1, messages1.size());

        // 检查缓存大小
        assertEquals(1, memory.getCacheSize());

        // 等待缓存过期
        Thread.sleep(1500);

        // 清理缓存
        memory.cleanupCache();

        // 缓存应该被清理
        assertEquals(0, memory.getCacheSize());

        // 再次加载文件（应该重新读取）
        List<Message> messages2 = memory.processReferences(input);
        assertEquals(1, messages2.size());
        assertEquals(1, memory.getCacheSize());
    }

    @Test
    void testClearCache() throws IOException {
        WarmMemory memory = new WarmMemory(tokenEstimator);

        // 加载文件
        String input = "分析 @" + tempTestFile.toString();
        List<Message> messages = memory.processReferences(input);
        assertEquals(1, messages.size());
        assertEquals(1, memory.getCacheSize());

        // 清除缓存
        memory.clearCache();

        // 缓存应该被清除
        assertEquals(0, memory.getCacheSize());
    }

    @Test
    void testTokenLimit() throws IOException {
        // 创建配置，设置很小的 token 限制
        ContextConfig.WarmMemoryConfig config = new ContextConfig.WarmMemoryConfig();
        config.setAtReferenceEnabled(true);
        config.setMaxFileTokens(10); // 很小的限制

        WarmMemory memory = new WarmMemory(tokenEstimator, config);

        // 加载文件（应该触发警告但仍然加载）
        String input = "分析 @" + tempTestFile.toString();
        List<Message> messages = memory.processReferences(input);

        // 应该返回消息
        assertNotNull(messages);
        assertEquals(1, messages.size());
    }

    @Test
    void testMultipleReferences() throws IOException {
        // 创建第二个临时文件
        Path tempTestFile2 = Paths.get(tempTestFile.getParent().toString(), "TestClass2.java");
        Files.writeString(tempTestFile2, "public class TestClass2 {\n    public void testMethod2() {\n        System.out.println(\"Hello World 2\");\n    }\n}");

        WarmMemory memory = new WarmMemory(tokenEstimator);

        // 测试输入包含多个 @ 引用
        String input = "分析 @" + tempTestFile.toString() + " 和 @" + tempTestFile2.toString();
        List<Message> messages = memory.processReferences(input);

        // 应该返回两个消息
        assertNotNull(messages);
        assertEquals(2, messages.size());
    }
}
