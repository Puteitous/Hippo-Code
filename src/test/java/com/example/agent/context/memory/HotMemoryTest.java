package com.example.agent.context.memory;

import com.example.agent.context.config.ContextConfig;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HotMemory 测试
 */
class HotMemoryTest {

    private HotMemory hotMemory;
    private TokenEstimator tokenEstimator;
    private static final String TEST_RULES_CONTENT = "# 项目规则\n- 规则1: 测试规则\n- 规则2: 另一个测试规则";
    private static final String TEST_MEMORY_CONTENT = "# 持久化记忆\n- 记忆1: 重要信息\n- 记忆2: 另一个重要信息";
    private static final String BASE_SYSTEM_PROMPT = "你是一个测试助手";

    private Path tempRulesFile;
    private Path tempMemoryFile;

    @BeforeEach
    void setUp() throws IOException {
        tokenEstimator = new SimpleTokenEstimator();

        // 创建临时文件
        tempRulesFile = Files.createTempFile(".hipporules", ".tmp");
        tempMemoryFile = Files.createTempFile("MEMORY", ".md.tmp");

        // 写入测试内容
        Files.writeString(tempRulesFile, TEST_RULES_CONTENT);
        Files.writeString(tempMemoryFile, TEST_MEMORY_CONTENT);
    }

    @Test
    void testHotMemoryCreation() {
        HotMemory memory = new HotMemory(tokenEstimator);
        assertNotNull(memory);
    }

    @Test
    void testLoadHippoRules() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();

        assertEquals(TEST_RULES_CONTENT, memory.getHippoRulesContent());
        assertTrue(memory.hasContent());
    }

    @Test
    void testLoadMemoryMd() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setMemoryFile(tempMemoryFile.toString());

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadMemoryMd();

        assertEquals(TEST_MEMORY_CONTENT, memory.getMemoryMdContent());
        assertTrue(memory.hasContent());
    }

    @Test
    void testLoadBothFiles() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());
        config.setMemoryFile(tempMemoryFile.toString());

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        assertEquals(TEST_RULES_CONTENT, memory.getHippoRulesContent());
        assertEquals(TEST_MEMORY_CONTENT, memory.getMemoryMdContent());
        assertTrue(memory.hasContent());
    }

    @Test
    void testEnhanceSystemPrompt() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());
        config.setMemoryFile(tempMemoryFile.toString());
        config.setInjectAtStartup(true);

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        String enhancedPrompt = memory.enhanceSystemPrompt(BASE_SYSTEM_PROMPT);

        // 验证增强后的提示词包含所有内容
        assertTrue(enhancedPrompt.contains(BASE_SYSTEM_PROMPT));
        assertTrue(enhancedPrompt.contains(TEST_RULES_CONTENT));
        assertTrue(enhancedPrompt.contains(TEST_MEMORY_CONTENT));
        assertTrue(enhancedPrompt.contains("# 项目规则 (.hipporules)"));
        assertTrue(enhancedPrompt.contains("# 持久化记忆 (MEMORY.md)"));

        // 验证 token 计算
        assertTrue(memory.getTotalTokens() > 0);
    }

    @Test
    void testInjectAtStartupDisabled() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());
        config.setMemoryFile(tempMemoryFile.toString());
        config.setInjectAtStartup(false);

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        String enhancedPrompt = memory.enhanceSystemPrompt(BASE_SYSTEM_PROMPT);

        // 应该只返回基础提示词
        assertEquals(BASE_SYSTEM_PROMPT, enhancedPrompt);
    }

    @Test
    void testNonExistentFiles() {
        // 创建配置，指向不存在的文件
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile("non_existent_rules.txt");
        config.setMemoryFile("non_existent_memory.txt");

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        // 应该返回空内容
        assertTrue(memory.getHippoRulesContent().isEmpty());
        assertTrue(memory.getMemoryMdContent().isEmpty());
        assertFalse(memory.hasContent());
    }

    @Test
    void testEmptyFiles() throws IOException {
        // 创建空文件
        Path emptyRulesFile = Files.createTempFile("empty_rules", ".tmp");
        Path emptyMemoryFile = Files.createTempFile("empty_memory", ".md.tmp");

        // 写入空内容
        Files.writeString(emptyRulesFile, "");
        Files.writeString(emptyMemoryFile, "");

        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(emptyRulesFile.toString());
        config.setMemoryFile(emptyMemoryFile.toString());

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        // 应该返回空内容
        assertTrue(memory.getHippoRulesContent().isEmpty());
        assertTrue(memory.getMemoryMdContent().isEmpty());
        assertFalse(memory.hasContent());
    }

    @Test
    void testReset() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());
        config.setMemoryFile(tempMemoryFile.toString());

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        assertTrue(memory.hasContent());

        memory.reset();

        // 重置后应该为空
        assertTrue(memory.getHippoRulesContent().isEmpty());
        assertTrue(memory.getMemoryMdContent().isEmpty());
        assertFalse(memory.hasContent());
        assertEquals(0, memory.getTotalTokens());
    }

    @Test
    void testReload() throws IOException {
        // 创建配置
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());
        config.setMemoryFile(tempMemoryFile.toString());

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        // 修改文件内容
        String newRulesContent = "# 新规则\n- 新规则1\n- 新规则2";
        Files.writeString(tempRulesFile, newRulesContent);

        // 重新加载
        memory.reload();

        // 应该加载新内容
        assertEquals(newRulesContent, memory.getHippoRulesContent());
        assertEquals(TEST_MEMORY_CONTENT, memory.getMemoryMdContent());
    }

    @Test
    void testNullConfig() {
        // 使用 null 配置
        HotMemory memory = new HotMemory(tokenEstimator, null);
        assertNotNull(memory);

        // 应该使用默认配置
        memory.loadHippoRules();
        memory.loadMemoryMd();

        // 应该返回空内容（默认文件不存在）
        assertTrue(memory.getHippoRulesContent().isEmpty());
        assertTrue(memory.getMemoryMdContent().isEmpty());
    }

    @Test
    void testTokenLimitWarning() throws IOException {
        // 创建配置，设置很小的 token 限制
        ContextConfig.HotMemoryConfig config = new ContextConfig.HotMemoryConfig();
        config.setRulesFile(tempRulesFile.toString());
        config.setMemoryFile(tempMemoryFile.toString());
        config.setMaxTokens(10); // 很小的限制

        HotMemory memory = new HotMemory(tokenEstimator, config);
        memory.loadHippoRules();
        memory.loadMemoryMd();

        // 增强系统提示词（应该触发警告）
        String enhancedPrompt = memory.enhanceSystemPrompt(BASE_SYSTEM_PROMPT);
        assertTrue(enhancedPrompt.length() > 0);
        // 虽然超过限制，但应该仍然返回内容
        assertNotNull(enhancedPrompt);
    }
}
