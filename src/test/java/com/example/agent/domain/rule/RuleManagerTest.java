package com.example.agent.domain.rule;

import com.example.agent.config.RuleConfig;
import com.example.agent.service.SimpleTokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleManager 边界条件测试
 *
 * 测试重点：
 * - null 配置处理
 * - null tokenEstimator 处理
 * - 不存在的文件处理
 * - 空文件/空内容处理
 * - reload 边界
 */
class RuleManagerTest {

    private SimpleTokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
    }

    @Test
    @DisplayName("构造函数 - null config")
    void testConstructorWithNullConfig() {
        RuleManager manager = new RuleManager(tokenEstimator, null);
        assertNotNull(manager);
        assertEquals("", manager.getHippoRulesContent());
        assertEquals("", manager.getMemoryMdContent());
    }

    @Test
    @DisplayName("构造函数 - null tokenEstimator")
    void testConstructorWithNullTokenEstimator() {
        RuleConfig config = new RuleConfig();
        assertDoesNotThrow(() -> new RuleManager(null, config));
    }

    @Test
    @DisplayName("构造函数 - 双参数都为 null")
    void testConstructorWithBothNull() {
        assertDoesNotThrow(() -> new RuleManager(null, null));
    }

    @Test
    @DisplayName("构造函数 - 单参数 null tokenEstimator")
    void testSingleArgConstructor() {
        assertDoesNotThrow(() -> new RuleManager(null));
    }

    @Test
    @DisplayName("边界 - 加载不存在的规则文件")
    void testLoadNonExistentRulesFile() {
        ContextConfig.RuleConfig config = new ContextConfig.RuleConfig();
        config.setRulesFile("nonexistent_rules.txt");
        RuleManager manager = new RuleManager(tokenEstimator, config);

        assertDoesNotThrow(() -> manager.loadHippoRules());
        assertEquals("", manager.getHippoRulesContent());
    }

    @Test
    @DisplayName("边界 - 加载不存在的记忆文件")
    void testLoadNonExistentMemoryFile() {
        ContextConfig.RuleConfig config = new ContextConfig.RuleConfig();
        config.setMemoryFile("nonexistent_memory.md");
        RuleManager manager = new RuleManager(tokenEstimator, config);

        assertDoesNotThrow(() -> manager.loadMemoryMd());
        assertEquals("", manager.getMemoryMdContent());
    }

    @Test
    @DisplayName("边界 - null 文件路径配置")
    void testNullFilePaths() {
        ContextConfig.RuleConfig config = new ContextConfig.RuleConfig();
        config.setRulesFile(null);
        config.setMemoryFile(null);
        RuleManager manager = new RuleManager(tokenEstimator, config);

        assertDoesNotThrow(() -> manager.loadHippoRules());
        assertDoesNotThrow(() -> manager.loadMemoryMd());
        assertEquals("", manager.getHippoRulesContent());
        assertEquals("", manager.getMemoryMdContent());
    }

    @Test
    @DisplayName("边界 - 空字符串文件路径")
    void testEmptyFilePaths() {
        ContextConfig.RuleConfig config = new ContextConfig.RuleConfig();
        config.setRulesFile("");
        config.setMemoryFile("");
        RuleManager manager = new RuleManager(tokenEstimator, config);

        assertDoesNotThrow(() -> manager.loadHippoRules());
        assertDoesNotThrow(() -> manager.loadMemoryMd());
        assertEquals("", manager.getHippoRulesContent());
        assertEquals("", manager.getMemoryMdContent());
    }

    @Test
    @DisplayName("边界 - 增强 null 系统提示词")
    void testEnhanceNullSystemPrompt() {
        RuleManager manager = new RuleManager(tokenEstimator);
        String result = manager.enhanceSystemPrompt(null);

        assertNotNull(result);
        assertTrue(result.contains("项目规则"));
    }

    @Test
    @DisplayName("边界 - 增强空字符串系统提示词")
    void testEnhanceEmptySystemPrompt() {
        RuleManager manager = new RuleManager(tokenEstimator);
        String result = manager.enhanceSystemPrompt("");

        assertNotNull(result);
        assertTrue(result.contains("项目规则"));
    }

    @Test
    @DisplayName("边界 - 禁用注入时返回原始提示词")
    void testInjectDisabled() {
        ContextConfig.RuleConfig config = new ContextConfig.RuleConfig();
        config.setInjectAtStartup(false);
        RuleManager manager = new RuleManager(tokenEstimator, config);

        String original = "original prompt";
        String result = manager.enhanceSystemPrompt(original);
        assertEquals(original, result);
    }

    @Test
    @DisplayName("边界 - reload 方法不报错")
    void testReload() {
        RuleManager manager = new RuleManager(tokenEstimator);
        assertDoesNotThrow(() -> manager.reload());
    }

    @Test
    @DisplayName("边界 - 初始 token 数为 0")
    void testInitialTokens() {
        RuleManager manager = new RuleManager(tokenEstimator);
        assertEquals(0, manager.getTotalTokens());
    }

    @Test
    @DisplayName("边界 - null tokenEstimator 下的 enhanceSystemPrompt")
    void testNullTokenEstimatorEnhance() {
        RuleManager manager = new RuleManager(null);
        assertDoesNotThrow(() -> manager.enhanceSystemPrompt("test"));
    }

    @Test
    @DisplayName("边界 - 连续多次 reload 不报错")
    void testMultipleReloads() {
        RuleManager manager = new RuleManager(tokenEstimator);
        assertDoesNotThrow(() -> {
            manager.reload();
            manager.reload();
            manager.reload();
        });
    }
}
