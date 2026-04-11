package com.example.agent.context.policy;

import com.example.agent.context.TrimPolicy;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextPolicyFactory 测试
 */
class ContextPolicyFactoryTest {

    private final TrimPolicy dummyTrimPolicy = new TrimPolicy() {
        @Override
        public List<Message> apply(List<Message> messages, int maxTokens, int maxMessages) {
            return messages;
        }
    };

    @Test
    void testCreateSimplePolicy() {
        ContextConfig config = new ContextConfig();
        config.setPolicy("simple");

        ContextPolicy policy = ContextPolicyFactory.create(config, dummyTrimPolicy);

        assertNotNull(policy);
        assertInstanceOf(SimplePolicy.class, policy);
        assertEquals("SimplePolicy", policy.getName());
    }

    @Test
    void testCreateDefaultPolicy() {
        ContextPolicy policy = ContextPolicyFactory.createDefault(dummyTrimPolicy);

        assertNotNull(policy);
        assertInstanceOf(SimplePolicy.class, policy);
    }

    @Test
    void testCreateWithNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> {
            ContextPolicyFactory.create(null, dummyTrimPolicy);
        });
    }

    @Test
    void testCreateWithUnknownPolicy() {
        ContextConfig config = new ContextConfig();
        config.setPolicy("unknown-policy");

        // 应该回退到 SimplePolicy
        ContextPolicy policy = ContextPolicyFactory.create(config, dummyTrimPolicy);

        assertNotNull(policy);
        assertInstanceOf(SimplePolicy.class, policy);
    }

    @Test
    void testIsSupported() {
        assertTrue(ContextPolicyFactory.isSupported("simple"));
        assertTrue(ContextPolicyFactory.isSupported("SIMPLE")); // 大小写不敏感

        assertFalse(ContextPolicyFactory.isSupported("three-tier"));
        assertFalse(ContextPolicyFactory.isSupported("unknown"));
        assertFalse(ContextPolicyFactory.isSupported(null));
    }

    @Test
    void testGetSupportedPolicies() {
        String[] policies = ContextPolicyFactory.getSupportedPolicies();

        assertNotNull(policies);
        assertEquals(1, policies.length);
        assertEquals("simple", policies[0]);
    }

    @Test
    void testDefaultPolicyInConfig() {
        ContextConfig config = new ContextConfig();

        // 默认应该是 simple
        assertEquals("simple", config.getPolicy());

        ContextPolicy policy = ContextPolicyFactory.create(config, dummyTrimPolicy);
        assertInstanceOf(SimplePolicy.class, policy);
    }
}
