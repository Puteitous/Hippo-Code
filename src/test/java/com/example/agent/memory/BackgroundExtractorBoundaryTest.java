package com.example.agent.memory;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class BackgroundExtractorBoundaryTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private SessionCompactionState compactionState;

    private BackgroundExtractor extractor;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSessionId = "test-session-" + System.currentTimeMillis();
        extractor = new BackgroundExtractor(testSessionId, tokenEstimator, llmClient, compactionState);
    }

    // ==================== 入口边界测试 ====================

    @Test
    void testCheckAndExtractWithNullDoesNothing() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        assertFalse(lock.get());
        assertDoesNotThrow(() -> extractor.checkAndExtract(null),
            "checkAndExtract(null) 不应抛出 NPE");
        assertFalse(lock.get(), "null 输入不应获取锁");
    }

    @Test
    void testCheckAndExtractWithEmptyDoesNothing() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        List<Message> emptyList = Collections.emptyList();

        assertFalse(lock.get());
        assertDoesNotThrow(() -> extractor.checkAndExtract(emptyList),
            "checkAndExtract(empty) 不应抛出异常");
        assertFalse(lock.get(), "空列表不应获取锁");
    }

    @Test
    void testRequestExtractionAfterCompactionWithNullDoesNothing() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        assertFalse(lock.get());
        assertDoesNotThrow(() -> extractor.requestExtractionAfterCompaction(null),
            "requestExtractionAfterCompaction(null) 不应抛出 NPE");
        assertFalse(lock.get(), "null 输入不应获取锁");
    }

    @Test
    void testRequestExtractionAfterCompactionWithEmptyDoesNothing() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        List<Message> emptyList = Collections.emptyList();

        assertFalse(lock.get());
        assertDoesNotThrow(() -> extractor.requestExtractionAfterCompaction(emptyList),
            "requestExtractionAfterCompaction(empty) 不应抛出异常");
        assertFalse(lock.get(), "空列表不应获取锁");
    }

    // ==================== 锁机制边界测试 ====================

    @Test
    void testConcurrentCheckAndExtractDoesNotCreateMultipleTasks() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        when(tokenEstimator.estimate(anyList())).thenReturn(20000);

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        int[] taskStarts = {0};

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    List<Message> messages = createConversation(20);
                    if (lock.compareAndSet(false, true)) {
                        taskStarts[0]++;
                    }
                    extractor.checkAndExtract(messages);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        assertTrue(taskStarts[0] <= 1,
            "并发情况下最多只能有一个任务被启动，实际: " + taskStarts[0]);
    }

    @Test
    void testLockIsAlwaysReleasedOnExceptionInCallback() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        java.lang.reflect.Method callbackMethod = BackgroundExtractor.class.getDeclaredMethod(
            "onMemoryExtractionCompleted", List.class);
        callbackMethod.setAccessible(true);

        lock.set(true);

        List<Message> badList = new ArrayList<>() {
            @Override
            public int size() {
                throw new RuntimeException("Simulated NPE in callback");
            }
        };

        assertDoesNotThrow(() -> callbackMethod.invoke(extractor, badList),
            "回调异常不应传播");

        assertFalse(lock.get(), "回调异常后锁应被释放");
    }

    @Test
    void testWatchdogForcesLockReleaseEventually() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        lock.set(true);

        Runnable watchdog = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock.get()) {
                    lock.set(false);
                }
            }
        };

        new Thread(watchdog).start();

        Thread.sleep(200);

        assertFalse(lock.get(), "看门狗应最终释放锁");
    }

    // ==================== 竞态条件边界测试 ====================

    @Test
    void testCheckAndExtractDuringExtractionDoesNotDeadlock() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch extractionDone = new CountDownLatch(1);

        new Thread(() -> {
            lock.set(true);
            extractionStarted.countDown();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.set(false);
                extractionDone.countDown();
            }
        }).start();

        extractionStarted.await(1, TimeUnit.SECONDS);

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                extractor.checkAndExtract(createConversation(5));
            }
        }, "提取过程中重复调用不应死锁");

        extractionDone.await(1, TimeUnit.SECONDS);

        assertFalse(lock.get(), "所有操作完成后锁应被释放");
    }

    @Test
    void testLockLeakageWithRapidSequentialCalls() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        when(tokenEstimator.estimate(anyList())).thenReturn(5000);

        for (int i = 0; i < 100; i++) {
            extractor.checkAndExtract(createConversation(3));
        }

        Thread.sleep(500);

        assertFalse(lock.get(), "快速连续调用后锁不应泄漏");
    }

    @Test
    void testBothEntryPointsCalledConcurrentlyDoNotLeakLock() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        when(tokenEstimator.estimate(anyList())).thenReturn(5000);

        CountDownLatch doneLatch = new CountDownLatch(40);

        for (int i = 0; i < 20; i++) {
            final int idx = i;
            new Thread(() -> {
                if (idx % 2 == 0) {
                    extractor.checkAndExtract(createConversation(5));
                } else {
                    extractor.requestExtractionAfterCompaction(createConversation(5));
                }
                doneLatch.countDown();
            }).start();
        }

        doneLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(500);

        assertFalse(lock.get(), "两个入口并发调用后锁不应泄漏");
    }

    // ==================== 异常路径锁保护测试 ====================

    @Test
    void testLockReleasedWhenSubAgentManagerIsNull() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        java.lang.reflect.Method method = BackgroundExtractor.class.getDeclaredMethod(
            "performExtraction", List.class);
        method.setAccessible(true);

        lock.set(true);

        assertDoesNotThrow(() -> method.invoke(extractor, createConversation(5)),
            "传统模式提取不应抛出异常");

        assertFalse(lock.get(), "传统模式提取完成后锁应释放");
    }

    @Test
    void testLockStateConsistencyAfterAllExitPaths() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        Runnable[] testCases = {
            () -> extractor.checkAndExtract(null),
            () -> extractor.checkAndExtract(Collections.emptyList()),
            () -> extractor.checkAndExtract(createConversation(1)),
            () -> extractor.requestExtractionAfterCompaction(null),
            () -> extractor.requestExtractionAfterCompaction(Collections.emptyList())
        };

        for (Runnable testCase : testCases) {
            lock.set(false);
            testCase.run();
            assertFalse(lock.get(), "每个退出路径后锁都应释放");
        }
    }

    private List<Message> createConversation(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                messages.add(Message.user("User message " + i));
            } else {
                messages.add(Message.assistant("Assistant message " + i));
            }
        }
        return messages;
    }
}
