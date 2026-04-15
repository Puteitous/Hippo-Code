package com.example.agent.memory;

import com.example.agent.llm.model.Message;
import com.example.agent.memory.classifier.RuleBasedClassifier;
import com.example.agent.memory.model.MemoryPriority;
import com.example.agent.memory.model.PrioritizedMessage;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import com.example.agent.service.TiktokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemorySystemTest {

    private TokenEstimator tokenEstimator;
    private RuleBasedClassifier classifier;
    private PriorityTrimPolicy policy;
    private MemorySystem memorySystem;

    @BeforeEach
    void setUp() {
        tokenEstimator = new TiktokenEstimator();
        classifier = new RuleBasedClassifier();
        policy = new PriorityTrimPolicy(tokenEstimator);
        memorySystem = new MemorySystem(tokenEstimator);
    }

    @Test
    void shouldClassifySystemMessageAsPinned() {
        Message systemMsg = Message.system("你是一个编程助手");
        PrioritizedMessage result = classifier.classify(systemMsg);

        assertEquals(MemoryPriority.PINNED, result.getPriority());
        assertTrue(result.calculateRetentionScore() > 90);
    }

    @Test
    void shouldClassifyUserRequirementAsHighPriority() {
        Message userMsg = Message.user("必须使用 Java 17 的新特性");
        PrioritizedMessage result = classifier.classify(userMsg);

        assertEquals(MemoryPriority.HIGH, result.getPriority());
    }

    @Test
    void shouldClassifyErrorToolResultAsHighPriority() {
        Message toolMsg = Message.toolResult("call_123", "bash", "Error: NullPointerException");
        PrioritizedMessage result = classifier.classify(toolMsg);

        assertEquals(MemoryPriority.HIGH, result.getPriority());
    }

    @Test
    void shouldKeepSystemMessageAlways() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("系统指令"));
        for (int i = 0; i < 50; i++) {
            messages.add(Message.user("用户输入 " + i));
            messages.add(Message.assistant("助手回复 " + i));
        }

        List<Message> result = policy.apply(messages, 1000, 50);

        assertTrue(result.stream().anyMatch(m -> "system".equals(m.getRole())));
    }

    @Test
    void shouldHaveCorrectPolicyName() {
        assertEquals("PriorityTrimPolicy", policy.getName());
    }

    @Test
    void memorySystemShouldEnableByDefault() {
        assertTrue(memorySystem.isEnabled());
        assertTrue(memorySystem.isSummaryEnabled());
    }

    @Test
    void shouldToggleMemorySystemShouldWork() {
        memorySystem.setEnabled(false);
        assertFalse(memorySystem.isEnabled());
        assertEquals("SlidingWindowPolicy", memorySystem.getTrimPolicy().getName());

        memorySystem.setEnabled(true);
        assertTrue(memorySystem.isEnabled());
        assertEquals("PriorityTrimPolicy", memorySystem.getTrimPolicy().getName());
    }
}
