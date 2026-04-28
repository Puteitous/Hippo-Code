package com.example.agent.context;

import com.example.agent.context.budget.BudgetThreshold;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContextWindow 重入保护测试")
class ContextWindowReentrancyTest {

    private ContextWindow contextWindow;
    private TokenEstimator tokenEstimator;
    private BudgetWarningInjector warningInjector;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        contextWindow = new ContextWindow(100000, tokenEstimator);
        warningInjector = new BudgetWarningInjector(contextWindow);
        warningInjector.register();
    }

    @Test
    @DisplayName("✅ 注入警告消息不会导致无限递归")
    void injectWarningShouldNotCauseInfiniteRecursion() {
        for (int i = 0; i < 10; i++) {
            contextWindow.addMessage(Message.user("Test message " + i + " " + "x".repeat(1000)));
        }

        assertDoesNotThrow(() -> {
            contextWindow.addMessage(Message.user("Trigger warning injection"));
        });
    }

    @Test
    @DisplayName("✅ 多次添加消息不会栈溢出")
    void multipleMessagesShouldNotStackOverflow() {
        String longContent = "x".repeat(5000);
        
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 20; i++) {
                contextWindow.addMessage(Message.user(longContent));
            }
        });
    }

    @Test
    @DisplayName("✅ 预算更新监听器正常工作")
    void budgetUpdateListenersWorkCorrectly() {
        contextWindow.addMessage(Message.user("Hello world"));
        
        TokenBudget budget = contextWindow.getBudget();
        assertTrue(budget.getCurrentTokens() > 0);
    }

    @Test
    @DisplayName("✅ 达到阈值时注入警告不会递归")
    void thresholdWarningInjectionShouldNotRecurse() {
        String veryLongContent = "x".repeat(50000);
        
        assertDoesNotThrow(() -> {
            contextWindow.addMessage(Message.user(veryLongContent));
            contextWindow.addMessage(Message.user(veryLongContent));
        });
        
        assertTrue(contextWindow.getBudget().getCurrentTokens() > 0);
    }

    @Test
    @DisplayName("✅ 自动压缩公告只注入一次")
    void autoCompactAnnouncementInjectedOnlyOnce() {
        String content = "x".repeat(30000);
        contextWindow.addMessage(Message.user(content));
        
        int warningCount = (int) contextWindow.getEffectiveMessages().stream()
            .filter(Message::isSystem)
            .count();
        
        assertTrue(warningCount <= 1, "自动压缩公告应该只注入一次");
    }

    @Test
    @DisplayName("✅ 清除警告后重新添加不会递归")
    void clearAndReAddShouldNotRecurse() {
        contextWindow.addMessage(Message.user("Test"));
        contextWindow.clearInjectedWarnings();
        
        assertDoesNotThrow(() -> {
            contextWindow.addMessage(Message.user("After clear"));
        });
    }

    @Test
    @DisplayName("✅ 替换消息列表不会递归")
    void replaceMessagesShouldNotRecurse() {
        contextWindow.addMessage(Message.user("Original"));
        
        assertDoesNotThrow(() -> {
            contextWindow.replaceMessages(java.util.List.of(
                Message.user("New message 1"),
                Message.user("New message 2")
            ));
        });
        
        assertEquals(2, contextWindow.size());
    }

    @Test
    @DisplayName("✅ 删除消息不会递归")
    void removeMessageShouldNotRecurse() {
        contextWindow.addMessage(Message.user("Message 1"));
        contextWindow.addMessage(Message.user("Message 2"));
        
        assertDoesNotThrow(() -> {
            contextWindow.removeMessage(0);
        });
        
        assertEquals(1, contextWindow.size());
    }

    @Test
    @DisplayName("✅ 清空上下文不会递归")
    void clearShouldNotRecurse() {
        contextWindow.addMessage(Message.user("Test"));
        
        assertDoesNotThrow(() -> {
            contextWindow.clear();
        });
        
        assertTrue(contextWindow.isEmpty());
    }

    @Test
    @DisplayName("✅ 并发添加消息不会死锁")
    void concurrentMessageAdditionShouldNotDeadlock() {
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    contextWindow.addMessage(Message.user("Thread " + index + " msg " + j));
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        assertDoesNotThrow(() -> {
            for (Thread thread : threads) {
                thread.join(5000);
            }
        });
    }
}
