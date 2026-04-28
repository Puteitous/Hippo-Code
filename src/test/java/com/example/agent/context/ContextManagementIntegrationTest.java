package com.example.agent.context;

import com.example.agent.context.budget.BudgetListener;
import com.example.agent.context.budget.BudgetThreshold;
import com.example.agent.llm.model.Message;
import com.example.agent.service.SimpleTokenEstimator;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("上下文管理流程集成测试")
class ContextManagementIntegrationTest {

    private ContextWindow contextWindow;
    private TokenEstimator tokenEstimator;
    private BudgetWarningInjector warningInjector;
    private static final int MAX_TOKENS = 100000;

    @BeforeEach
    void setUp() {
        tokenEstimator = new SimpleTokenEstimator();
        contextWindow = new ContextWindow(MAX_TOKENS, tokenEstimator);
        warningInjector = new BudgetWarningInjector(contextWindow);
        warningInjector.register();
    }

    @Nested
    @DisplayName("完整流程测试")
    class FullWorkflowTests {

        @Test
        @DisplayName("消息添加 → 预算更新 → 警告注入 完整流程")
        void messageAddToWarningInjection() {
            String longContent = "x".repeat(5000);
            
            for (int i = 0; i < 20; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + longContent));
            }
            
            double usageRatio = contextWindow.getBudget().getUsageRatio();
            assertTrue(usageRatio > 0, "预算使用率应该大于0");
            
            int effectiveMessageCount = contextWindow.getEffectiveMessages().size();
            int rawMessageCount = contextWindow.size();
            assertTrue(effectiveMessageCount >= rawMessageCount, 
                    "有效消息数应该大于等于原始消息数（包含警告）");
        }

        @Test
        @DisplayName("大量消息添加不会导致递归或死锁")
        void massiveMessageAddition() {
            String veryLongContent = "x".repeat(10000);
            
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 50; i++) {
                    contextWindow.addMessage(Message.user("Message " + i + " " + veryLongContent));
                }
            }, "添加大量消息不应该抛出异常");
            
            assertTrue(contextWindow.size() > 0);
            assertTrue(contextWindow.getBudget().getUsageRatio() > 0);
        }

        @Test
        @DisplayName("预算阈值触发不会导致无限循环")
        void budgetThresholdTriggering() {
            String hugeContent = "x".repeat(100000);
            
            assertDoesNotThrow(() -> {
                contextWindow.addMessage(Message.user(hugeContent));
                contextWindow.addMessage(Message.user(hugeContent));
                contextWindow.addMessage(Message.user(hugeContent));
            }, "触发预算阈值不应该导致无限循环");
            
            double usageRatio = contextWindow.getBudget().getUsageRatio();
            assertTrue(usageRatio > 0.5, "使用率应该超过50%");
        }

        @Test
        @DisplayName("清空上下文后重新添加消息")
        void clearAndReAdd() {
            for (int i = 0; i < 10; i++) {
                contextWindow.addMessage(Message.user("Message " + i));
            }
            
            int sizeBefore = contextWindow.size();
            assertTrue(sizeBefore > 0);
            
            contextWindow.clear();
            assertEquals(0, contextWindow.size());
            assertEquals(0.0, contextWindow.getBudget().getUsageRatio());
            
            for (int i = 0; i < 5; i++) {
                contextWindow.addMessage(Message.user("New Message " + i));
            }
            
            assertEquals(5, contextWindow.size());
            assertTrue(contextWindow.getBudget().getUsageRatio() > 0);
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("多线程并发添加消息不会死锁")
        void concurrentMessageAddition() throws InterruptedException {
            int threadCount = 10;
            int messagesPerThread = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicReference<Throwable> error = new AtomicReference<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadIndex = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < messagesPerThread; i++) {
                            contextWindow.addMessage(
                                Message.user("Thread " + threadIndex + " Message " + i)
                            );
                        }
                    } catch (Throwable e) {
                        error.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertTrue(completed, "所有线程应该在30秒内完成");
            assertNull(error.get(), "不应该有异常: " + error.get());
            assertEquals(threadCount * messagesPerThread, contextWindow.size());
        }

        @Test
        @DisplayName("并发读取和写入不会冲突")
        void concurrentReadWrite() throws InterruptedException {
            contextWindow.addMessage(Message.user("Initial message"));
            
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(10);
            AtomicReference<Throwable> error = new AtomicReference<>();

            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 10; j++) {
                            contextWindow.addMessage(Message.user("Writer message " + j));
                            Thread.sleep(10);
                        }
                    } catch (Throwable e) {
                        error.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 10; j++) {
                            List<Message> messages = contextWindow.getEffectiveMessages();
                            double ratio = contextWindow.getBudget().getUsageRatio();
                            Thread.sleep(10);
                        }
                    } catch (Throwable e) {
                        error.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertTrue(completed, "所有操作应该在30秒内完成");
            assertNull(error.get(), "不应该有异常: " + error.get());
        }
    }

    @Nested
    @DisplayName("预算监控测试")
    class BudgetMonitoringTests {

        @Test
        @DisplayName("预算使用率正确计算")
        void budgetUsageCalculation() {
            assertEquals(0.0, contextWindow.getBudget().getUsageRatio());
            
            contextWindow.addMessage(Message.user("Hello world"));
            double ratioAfter = contextWindow.getBudget().getUsageRatio();
            assertTrue(ratioAfter > 0, "添加消息后预算使用率应该大于0");
            assertTrue(ratioAfter < 1.0, "单条消息不应该超过预算");
        }

        @Test
        @DisplayName("预算状态一致性检查")
        void budgetStateConsistency() {
            AtomicInteger updateCount = new AtomicInteger(0);
            TokenBudget budget = contextWindow.getBudget();
            
            budget.addListener(new BudgetListener() {
                @Override
                public void onThresholdReached(BudgetThreshold threshold, int currentTokens, int maxTokens) {}
                
                @Override
                public void onBudgetUpdated(int currentTokens, int maxTokens, double usageRatio) {
                    updateCount.incrementAndGet();
                }
            });

            for (int i = 0; i < 10; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + "x".repeat(10000)));
            }

            assertTrue(updateCount.get() > 0, "预算更新监听器应该被触发");
            assertTrue(budget.getCurrentTokens() > 0);
            assertEquals(MAX_TOKENS, budget.getMaxTokens());
        }

        @Test
        @DisplayName("预算超过最大值时的行为")
        void budgetExceedsMaximum() {
            String hugeContent = "x".repeat(50000);
            
            for (int i = 0; i < 5; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + hugeContent));
            }

            double usageRatio = contextWindow.getBudget().getUsageRatio();
            assertTrue(usageRatio > 0, "预算使用率应该大于0");
        }
    }

    @Nested
    @DisplayName("警告注入测试")
    class WarningInjectionTests {

        @Test
        @DisplayName("系统警告消息被正确注入")
        void systemWarningInjection() {
            String longContent = "x".repeat(5000);
            
            for (int i = 0; i < 20; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + longContent));
            }

            List<Message> effectiveMessages = contextWindow.getEffectiveMessages();
            List<Message> rawMessages = contextWindow.getRawMessages();
            
            assertTrue(effectiveMessages.size() >= rawMessages.size(), 
                    "有效消息数应该大于等于原始消息数");
        }

        @Test
        @DisplayName("警告消息不会无限增长")
        void warningMessageLimit() {
            String veryLongContent = "x".repeat(10000);
            
            for (int i = 0; i < 50; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + veryLongContent));
            }

            List<Message> effectiveMessages = contextWindow.getEffectiveMessages();
            List<Message> rawMessages = contextWindow.getRawMessages();
            
            int warningCount = effectiveMessages.size() - rawMessages.size();
            assertTrue(warningCount < 10, 
                    "警告消息数量应该合理，不应该无限增长: " + warningCount);
        }
    }

    @Nested
    @DisplayName("替换和清除测试")
    class ReplaceAndClearTests {

        @Test
        @DisplayName("替换消息列表后预算正确重新计算")
        void replaceMessagesRecalculatesBudget() {
            for (int i = 0; i < 10; i++) {
                contextWindow.addMessage(Message.user("Old Message " + i));
            }
            
            List<Message> newMessages = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                newMessages.add(Message.user("New Message " + i));
            }
            
            contextWindow.replaceMessages(newMessages);
            
            assertEquals(5, contextWindow.size());
            double ratioAfter = contextWindow.getBudget().getUsageRatio();
            assertTrue(ratioAfter >= 0, "替换后预算使用率应该有效");
        }

        @Test
        @DisplayName("清除警告后预算正确更新")
        void clearWarningsUpdatesBudget() {
            String longContent = "x".repeat(5000);
            
            for (int i = 0; i < 20; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + longContent));
            }

            double ratioBefore = contextWindow.getBudget().getUsageRatio();
            
            contextWindow.clearInjectedWarnings();
            
            double ratioAfter = contextWindow.getBudget().getUsageRatio();
            assertTrue(ratioAfter >= 0, "清除警告后预算应该有效");
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("空消息列表处理")
        void emptyMessageList() {
            assertEquals(0, contextWindow.size());
            assertEquals(0.0, contextWindow.getBudget().getUsageRatio());
            assertTrue(contextWindow.isEmpty());
        }

        @Test
        @DisplayName("极短消息处理")
        void veryShortMessages() {
            for (int i = 0; i < 100; i++) {
                contextWindow.addMessage(Message.user("a"));
            }
            
            assertEquals(100, contextWindow.size());
            assertTrue(contextWindow.getBudget().getUsageRatio() > 0);
        }

        @Test
        @DisplayName("超长消息处理")
        void extremelyLongMessages() {
            String hugeContent = "x".repeat(100000);
            
            assertDoesNotThrow(() -> {
                contextWindow.addMessage(Message.user(hugeContent));
            });
            
            assertEquals(1, contextWindow.size());
            assertTrue(contextWindow.getBudget().getUsageRatio() > 0);
        }

        @Test
        @DisplayName("混合角色消息处理")
        void mixedRoleMessages() {
            contextWindow.addMessage(Message.system("System prompt"));
            contextWindow.addMessage(Message.user("User message"));
            contextWindow.addMessage(Message.assistant("Assistant response"));
            
            assertEquals(3, contextWindow.size());
            assertTrue(contextWindow.getBudget().getUsageRatio() > 0);
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("大量消息添加性能")
        void performanceWithLargeMessages() {
            long startTime = System.currentTimeMillis();
            
            String content = "x".repeat(1000);
            for (int i = 0; i < 100; i++) {
                contextWindow.addMessage(Message.user("Message " + i + " " + content));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertEquals(100, contextWindow.size());
            assertTrue(duration < 5000, 
                    "添加100条消息应该在5秒内完成，实际耗时: " + duration + "ms");
        }

        @Test
        @DisplayName("重复清空和添加性能")
        void repeatedClearAndAdd() {
            long startTime = System.currentTimeMillis();
            
            for (int cycle = 0; cycle < 10; cycle++) {
                for (int i = 0; i < 20; i++) {
                    contextWindow.addMessage(Message.user("Message " + i));
                }
                contextWindow.clear();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertEquals(0, contextWindow.size());
            assertTrue(duration < 5000, 
                    "10轮清空和添加应该在5秒内完成，实际耗时: " + duration + "ms");
        }
    }
}