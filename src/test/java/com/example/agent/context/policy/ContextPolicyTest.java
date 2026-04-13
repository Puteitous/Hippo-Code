package com.example.agent.context.policy;

import com.example.agent.config.CacheConfig;
import com.example.agent.config.IndexConfig;
import com.example.agent.config.RuleConfig;
import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.SimpleTokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextPolicy 策略切换测试
 */
class ContextPolicyTest {

    private ConversationManager conversationManager;
    private TrimPolicy trimPolicy;
    private SimpleTokenEstimator tokenEstimator;
    private static final String SYSTEM_PROMPT = "你是一个测试助手";

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        trimPolicy = new TrimPolicy() {
            @Override
            public List<Message> apply(List<Message> messages, int maxTokens, int maxMessages) {
                // 简单实现：直接返回，不做裁剪
                return messages;
            }
        };
        conversationManager = new ConversationManager(SYSTEM_PROMPT, tokenEstimator);
    }

    @Test
    void testSimplePolicyCreation() {
        SimplePolicy policy = new SimplePolicy(trimPolicy);
        assertNotNull(policy);
        assertEquals("SimplePolicy", policy.getName());
    }

    @Test
    void testSimplePolicyBuildContext() {
        SimplePolicy policy = new SimplePolicy(trimPolicy);

        // 添加一些历史消息
        conversationManager.addUserMessage("你好");
        conversationManager.addAssistantMessage(Message.assistant("你好！有什么可以帮你的？"));

        // 构建上下文
        List<Message> context = policy.buildContext("测试输入", conversationManager, 10000);

        // 验证结果
        assertNotNull(context);
        assertTrue(context.size() >= 3); // 系统消息 + 历史 + 新输入

        // 验证系统消息存在
        assertEquals("system", context.get(0).getRole());
        assertEquals(SYSTEM_PROMPT, context.get(0).getContent());

        // 验证用户输入被添加
        boolean hasTestInput = context.stream()
                .anyMatch(m -> "user".equals(m.getRole()) && "测试输入".equals(m.getContent()));
        assertTrue(hasTestInput, "用户输入应该被添加到上下文中");
    }

    @Test
    void testSimplePolicyWithNullUserInput() {
        SimplePolicy policy = new SimplePolicy(trimPolicy);

        conversationManager.addUserMessage("已有消息");
        List<Message> context = policy.buildContext(null, conversationManager, 10000);

        assertNotNull(context);
        // 不应该添加新的用户消息
        long userMessageCount = context.stream()
                .filter(m -> "user".equals(m.getRole()))
                .count();
        assertEquals(1, userMessageCount);
    }

    @Test
    void testSimplePolicyWithEmptyUserInput() {
        SimplePolicy policy = new SimplePolicy(trimPolicy);

        conversationManager.addUserMessage("已有消息");
        List<Message> context = policy.buildContext("", conversationManager, 10000);

        assertNotNull(context);
        // 不应该添加新的用户消息
        long userMessageCount = context.stream()
                .filter(m -> "user".equals(m.getRole()))
                .count();
        assertEquals(1, userMessageCount);
    }

    @Test
    void testSimplePolicyWithNullBaseManager() {
        SimplePolicy policy = new SimplePolicy(trimPolicy);

        assertThrows(IllegalArgumentException.class, () -> {
            policy.buildContext("输入", null, 10000);
        });
    }

    @Test
    void testSimplePolicyWithDuplicateInput() {
        SimplePolicy policy = new SimplePolicy(trimPolicy);

        // 添加与输入相同的消息
        conversationManager.addUserMessage("重复输入");

        // 再次添加相同的输入
        List<Message> context = policy.buildContext("重复输入", conversationManager, 10000);

        // 验证没有重复添加
        long duplicateCount = context.stream()
                .filter(m -> "user".equals(m.getRole()) && "重复输入".equals(m.getContent()))
                .count();
        assertEquals(1, duplicateCount, "不应该重复添加相同的用户输入");
    }

    @Test
    void testConfigPolicySelection() {
        // 测试配置中的策略选择
        ContextConfig config = new ContextConfig();

        // 默认应该是 simple
        assertEquals("simple", config.getPolicy());

        // 可以切换到 three-tier
        config.setPolicy("three-tier");
        assertEquals("three-tier", config.getPolicy());
    }

    @Test
    void testRuleConfig() {
        RuleConfig rule = new RuleConfig();

        assertNotNull(rule);
        assertEquals(".hipporules", rule.getRulesFile());
        assertEquals("MEMORY.md", rule.getMemoryFile());
        assertEquals(8000, rule.getMaxTokens());
        assertTrue(rule.isInjectAtStartup());
    }

    @Test
    void testCacheConfig() {
        CacheConfig cache = new CacheConfig();

        assertNotNull(cache);
        assertEquals(4000, cache.getMaxFileTokens());
        assertEquals(300, cache.getCacheTtlSeconds());
    }

    @Test
    void testIndexConfig() {
        IndexConfig index = new IndexConfig();

        assertNotNull(index);
        assertTrue(index.isEnabled());
        assertEquals(3, index.getMaxResults());
        assertEquals(5000, index.getMaxTokens());
    }
}
