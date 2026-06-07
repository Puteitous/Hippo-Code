package com.example.agent.web.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionCancelManager 单元测试")
class SessionCancelManagerTest {

    private final SessionCancelManager manager = SessionCancelManager.getInstance();

    @AfterEach
    void tearDown() {
        manager.resetAll();
    }

    @Nested
    @DisplayName("基础操作")
    class BasicOperations {

        @Test
        @DisplayName("未取消的会话返回 false")
        void unknownSession_returnsFalse() {
            assertFalse(manager.isCancelled("session-unknown"));
        }

        @Test
        @DisplayName("null sessionId 返回 false")
        void nullSessionId_returnsFalse() {
            assertFalse(manager.isCancelled(null));
        }

        @Test
        @DisplayName("空字符串 sessionId 返回 false")
        void emptySessionId_returnsFalse() {
            assertFalse(manager.isCancelled(""));
        }

        @Test
        @DisplayName("取消后 isCancelled 返回 true")
        void cancel_marksSessionAsCancelled() {
            manager.cancel("session-1");
            assertTrue(manager.isCancelled("session-1"));
        }

        @Test
        @DisplayName("reset 后 isCancelled 返回 false")
        void reset_clearsCancellation() {
            manager.cancel("session-1");
            manager.reset("session-1");
            assertFalse(manager.isCancelled("session-1"));
        }

        @Test
        @DisplayName("resetAll 清除所有会话的取消状态")
        void resetAll_clearsAllSessions() {
            manager.cancel("session-a");
            manager.cancel("session-b");
            manager.resetAll();

            assertFalse(manager.isCancelled("session-a"));
            assertFalse(manager.isCancelled("session-b"));
        }

        @Test
        @DisplayName("cancel(null) 不应抛异常")
        void cancelNull_doesNotThrow() {
            assertDoesNotThrow(() -> manager.cancel(null));
        }

        @Test
        @DisplayName("cancel(\"\") 不应抛异常")
        void cancelEmpty_doesNotThrow() {
            assertDoesNotThrow(() -> manager.cancel(""));
        }

        @Test
        @DisplayName("重复 cancel 幂等")
        void repeatedCancel_isIdempotent() {
            manager.cancel("session-1");
            manager.cancel("session-1");
            manager.cancel("session-1");

            assertTrue(manager.isCancelled("session-1"));
        }

        @Test
        @DisplayName("reset 后重新 cancel 可再次标记")
        void resetThenCancel_worksAgain() {
            manager.cancel("session-1");
            manager.reset("session-1");
            assertFalse(manager.isCancelled("session-1"));

            manager.cancel("session-1");
            assertTrue(manager.isCancelled("session-1"));
        }
    }

    @Nested
    @DisplayName("隔离性")
    class IsolationTests {

        @Test
        @DisplayName("不同 session 的取消状态互不干扰")
        void differentSessions_areIndependent() {
            manager.cancel("session-a");

            assertTrue(manager.isCancelled("session-a"));
            assertFalse(manager.isCancelled("session-b"));
            assertFalse(manager.isCancelled("session-c"));

            manager.cancel("session-b");
            assertTrue(manager.isCancelled("session-a"));
            assertTrue(manager.isCancelled("session-b"));
            assertFalse(manager.isCancelled("session-c"));
        }

        @Test
        @DisplayName("reset 只清除指定会话，不影响其他")
        void resetOnlyTargetSession() {
            manager.cancel("session-a");
            manager.cancel("session-b");

            manager.reset("session-a");

            assertFalse(manager.isCancelled("session-a"));
            assertTrue(manager.isCancelled("session-b"));
        }
    }

    @Nested
    @DisplayName("并发安全")
    class ConcurrencyTests {

        @Test
        @DisplayName("多线程同时 cancel 同一 session 不抛异常")
        void concurrentCancel_sameSession_doesNotThrow() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicBoolean anyError = new AtomicBoolean(false);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        manager.cancel("concurrent-session");
                    } catch (Exception e) {
                        anyError.set(true);
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(anyError.get());
            assertTrue(manager.isCancelled("concurrent-session"));
        }

        @Test
        @DisplayName("一个线程 cancel，另一线程检查 isCancelled")
        void cancelFromOneThread_checkFromAnother() throws InterruptedException {
            CountDownLatch cancelDone = new CountDownLatch(1);
            AtomicBoolean checkResult = new AtomicBoolean(false);

            // 线程 A：取消会话
            new Thread(() -> {
                manager.cancel("cross-thread-session");
                cancelDone.countDown();
            }).start();

            // 线程 B：等待取消后检查
            new Thread(() -> {
                try {
                    cancelDone.await();
                } catch (InterruptedException ignored) {
                }
                checkResult.set(manager.isCancelled("cross-thread-session"));
            }).start();

            cancelDone.await(5, TimeUnit.SECONDS);
            Thread.sleep(200); // 给线程 B 时间执行

            assertTrue(checkResult.get(),
                "一个线程 cancel 后，另一线程应能看到取消状态");
        }
    }
}
