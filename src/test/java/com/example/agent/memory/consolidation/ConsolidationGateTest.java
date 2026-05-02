package com.example.agent.memory.consolidation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsolidationGate 三重门测试")
class ConsolidationGateTest {

    @TempDir
    Path tempDir;

    private ConsolidationGate gate;
    private Path memoryDir;

    @BeforeEach
    void setUp() {
        memoryDir = tempDir.resolve("memory");
        try {
            Files.createDirectories(memoryDir);
        } catch (IOException e) {
            fail("创建目录失败：" + e.getMessage());
        }
        gate = new ConsolidationGate(memoryDir);
    }

    @Test
    @DisplayName("门 1 拦截：距离上次整理不足 24 小时")
    void testGate1_TimeNotReached() {
        gate.markConsolidationComplete();

        boolean shouldConsolidate = gate.shouldConsolidate();

        assertFalse(shouldConsolidate, "门 1 应该拦截：距离上次整理不足 24 小时");
    }

    @Test
    @DisplayName("门 2 拦截：新会话数不足 3 个")
    void testGate2_NotEnoughSessions() {
        gate.reset();

        gate.registerSession("session-1");

        simulateTimePassed(Duration.ofHours(25));

        boolean shouldConsolidate = gate.shouldConsolidate();

        assertFalse(shouldConsolidate, "门 2 应该拦截：新会话数不足 3 个");
    }

    @Test
    @DisplayName("全部门通过：24 小时后 + 3 个新会话")
    void testAllGatesPass() {
        gate.reset();

        gate.registerSession("session-1");
        gate.registerSession("session-2");
        gate.registerSession("session-3");

        simulateTimePassed(Duration.ofHours(25));

        boolean shouldConsolidate = gate.shouldConsolidate();

        assertTrue(shouldConsolidate, "三重门应该全部通过");
        
        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertTrue(info.lockHeld, "锁应该已被获取");
    }

    @Test
    @DisplayName("进程崩溃恢复：重启后状态正确")
    void testCrashRecovery() throws InterruptedException {
        gate.registerSession("session-1");
        gate.registerSession("session-2");
        gate.registerSession("session-3");

        simulateTimePassed(Duration.ofHours(25));

        assertTrue(gate.shouldConsolidate());

        gate = new ConsolidationGate(memoryDir);

        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertEquals(3, info.newSessionCount, "新会话数应该恢复为 3");
        assertFalse(info.lockHeld, "重启后锁应该已释放");
    }

    @Test
    @DisplayName("防重入：并发调用只有一次获取锁成功")
    void testPreventReentry() throws InterruptedException {
        gate.reset();

        for (int i = 1; i <= 5; i++) {
            gate.registerSession("session-" + i);
        }

        simulateTimePassed(Duration.ofHours(25));

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean result = gate.shouldConsolidate();
                    if (result) {
                        successCount.incrementAndGet();
                    }
                    doneLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "并发测试超时");
        assertEquals(1, successCount.get(), "应该只有一次获取锁成功");
    }

    @Test
    @DisplayName("连续失败 3 次后暂停触发")
    void testConsecutiveFailuresPause() {
        gate.reset();

        for (int i = 1; i <= 3; i++) {
            gate.registerSession("session-" + i);
        }

        simulateTimePassed(Duration.ofHours(25));

        for (int i = 0; i < 3; i++) {
            gate.shouldConsolidate();
            gate.markConsolidationFailed();
            gate.releaseLockOnly();
            simulateTimePassed(Duration.ofHours(25));
        }

        boolean shouldConsolidate = gate.shouldConsolidate();
        assertFalse(shouldConsolidate, "连续失败 3 次后应该暂停触发");
    }

    @Test
    @DisplayName("LRU 策略：recentSessionIds 最多 200 个")
    void testLRUCapacity() {
        for (int i = 1; i <= 250; i++) {
            gate.registerSession("session-" + i);
        }

        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertEquals(200, info.recentSessionCount, "recentSessionIds 最多应该只有 200 个");
        assertEquals(250, info.newSessionCount, "新会话计数应该是 250");
    }

    @Test
    @DisplayName("标记会话为已处理后计数减少")
    void testMarkSessionAsProcessed() {
        gate.registerSession("session-1");
        gate.registerSession("session-2");
        gate.registerSession("session-3");

        assertEquals(3, gate.getInfo().newSessionCount);

        gate.markSessionAsProcessed("session-1");
        gate.markSessionAsProcessed("session-2");

        assertEquals(1, gate.getInfo().newSessionCount, "标记后新会话数应该减少");
    }

    @Test
    @DisplayName("整理完成后状态重置")
    void testConsolidationCompleteResetsState() {
        for (int i = 1; i <= 3; i++) {
            gate.registerSession("session-" + i);
        }

        simulateTimePassed(Duration.ofHours(25));

        assertTrue(gate.shouldConsolidate());

        gate.markConsolidationComplete();

        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertEquals(0, info.newSessionCount, "新会话数应该重置为 0");
        assertEquals(0, info.recentSessionCount, "recentSessionIds 应该清空");
        assertEquals(0, info.consecutiveFailures, "失败计数应该重置为 0");
        assertFalse(info.lockHeld, "锁应该已释放");
    }

    private void simulateTimePassed(Duration duration) {
        try {
            Path stateFile = memoryDir.resolve(".consolidation_state");
            
            ConsolidationGate.ConsolidationInfo info = gate.getInfo();
            
            Instant oldTime = Instant.now().minus(duration);
            
            String json = """
                {
                  "lastConsolidatedAt" : "%s",
                  "recentSessionIds" : [],
                  "newSessionCountSinceLast" : %d,
                  "consecutiveFailures" : %d
                }
                """.formatted(oldTime.toString(), info.newSessionCount, info.consecutiveFailures);
            
            Files.writeString(stateFile, json);
            
            gate = new ConsolidationGate(memoryDir);
        } catch (IOException e) {
            fail("模拟时间流逝失败：" + e.getMessage());
        }
    }
}
