package com.example.agent.memory;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConsolidationGate 完整测试
 * 
 * 验收场景：
 * 1. 门 1 拦截：2 小时内触发 shouldConsolidate → 返回 false
 * 2. 门 2 拦截：仅有 1 个新会话时触发 → 返回 false
 * 3. 全部门通过：24 小时后 + 3 个新会话 → 返回 true
 * 4. 进程崩溃恢复：重启后状态正确
 * 5. 防重入：上一次整合未完成时再次触发 → 只有一次获取锁成功
 */
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
        // 模拟刚完成整理（lastConsolidatedAt = now）
        gate.markConsolidationComplete();

        // 立即检查
        boolean shouldConsolidate = gate.shouldConsolidate();

        assertFalse(shouldConsolidate, "门 1 应该拦截：距离上次整理不足 24 小时");
    }

    @Test
    @DisplayName("门 2 拦截：新会话数不足 3 个")
    void testGate2_NotEnoughSessions() {
        // 重置状态（模拟 24 小时后）
        gate.reset();

        // 只注册 1 个新会话
        gate.registerSession("session-1");

        // 手动修改状态文件，模拟 24 小时前
        simulateTimePassed(Duration.ofHours(25));

        boolean shouldConsolidate = gate.shouldConsolidate();

        assertFalse(shouldConsolidate, "门 2 应该拦截：新会话数不足 3 个");
    }

    @Test
    @DisplayName("全部门通过：24 小时后 + 3 个新会话")
    void testAllGatesPass() {
        // 重置状态
        gate.reset();

        // 注册 3 个新会话
        gate.registerSession("session-1");
        gate.registerSession("session-2");
        gate.registerSession("session-3");

        // 模拟 24 小时后
        simulateTimePassed(Duration.ofHours(25));

        boolean shouldConsolidate = gate.shouldConsolidate();

        assertTrue(shouldConsolidate, "三重门应该全部通过");
        
        // 验证锁已获取
        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertTrue(info.lockHeld, "锁应该已被获取");
    }

    @Test
    @DisplayName("进程崩溃恢复：重启后状态正确")
    void testCrashRecovery() throws InterruptedException {
        // 注册 3 个会话
        gate.registerSession("session-1");
        gate.registerSession("session-2");
        gate.registerSession("session-3");

        // 模拟 24 小时后
        simulateTimePassed(Duration.ofHours(25));

        // 触发整理（获取锁）
        assertTrue(gate.shouldConsolidate());

        // 模拟崩溃：不调用 markConsolidationComplete()
        // 直接创建新的 gate 实例（模拟重启）
        gate = new ConsolidationGate(memoryDir);

        // 验证状态恢复
        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertEquals(3, info.newSessionCount, "新会话数应该恢复为 3");
        assertFalse(info.lockHeld, "重启后锁应该已释放");

        // 再次检查：应该能通过（因为锁已释放）
        // 但时间门可能不通过（取决于 simulateTimePassed 的实现）
        // 这里主要验证状态持久化成功
    }

    @Test
    @DisplayName("防重入：并发调用只有一次获取锁成功")
    void testPreventReentry() throws InterruptedException {
        // 重置状态
        gate.reset();

        // 注册 5 个会话
        for (int i = 1; i <= 5; i++) {
            gate.registerSession("session-" + i);
        }

        // 模拟 24 小时后
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

        // 同时启动所有线程
        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "并发测试超时");
        assertEquals(1, successCount.get(), "应该只有一次获取锁成功");
    }

    @Test
    @DisplayName("连续失败 3 次后暂停触发")
    void testConsecutiveFailuresPause() {
        // 重置状态
        gate.reset();

        // 注册 3 个会话
        for (int i = 1; i <= 3; i++) {
            gate.registerSession("session-" + i);
        }

        // 模拟 24 小时后
        simulateTimePassed(Duration.ofHours(25));

        // 第一次失败
        gate.shouldConsolidate();
        gate.markConsolidationFailed();

        // 仅释放锁（保留失败计数）
        gate.releaseLockOnly();
        simulateTimePassed(Duration.ofHours(25));

        // 第二次失败
        gate.shouldConsolidate();
        gate.markConsolidationFailed();

        // 仅释放锁
        gate.releaseLockOnly();
        simulateTimePassed(Duration.ofHours(25));

        // 第三次失败
        gate.shouldConsolidate();
        gate.markConsolidationFailed();

        // 仅释放锁
        gate.releaseLockOnly();
        simulateTimePassed(Duration.ofHours(25));

        // 第四次：应该被拦截
        boolean shouldConsolidate = gate.shouldConsolidate();
        assertFalse(shouldConsolidate, "连续失败 3 次后应该暂停触发");
    }

    @Test
    @DisplayName("LRU 策略：recentSessionIds 最多 200 个")
    void testLRUCapacity() {
        // 注册 250 个会话
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
        // 注册 3 个会话
        gate.registerSession("session-1");
        gate.registerSession("session-2");
        gate.registerSession("session-3");

        assertEquals(3, gate.getInfo().newSessionCount);

        // 标记 2 个为已处理
        gate.markSessionAsProcessed("session-1");
        gate.markSessionAsProcessed("session-2");

        assertEquals(1, gate.getInfo().newSessionCount, "标记后新会话数应该减少");
    }

    @Test
    @DisplayName("整理完成后状态重置")
    void testConsolidationCompleteResetsState() {
        // 注册 3 个会话
        for (int i = 1; i <= 3; i++) {
            gate.registerSession("session-" + i);
        }

        // 模拟 24 小时后
        simulateTimePassed(Duration.ofHours(25));

        // 触发整理
        assertTrue(gate.shouldConsolidate());

        // 标记完成
        gate.markConsolidationComplete();

        // 验证状态重置
        ConsolidationGate.ConsolidationInfo info = gate.getInfo();
        assertEquals(0, info.newSessionCount, "新会话数应该重置为 0");
        assertEquals(0, info.recentSessionCount, "recentSessionIds 应该清空");
        assertEquals(0, info.consecutiveFailures, "失败计数应该重置为 0");
        assertFalse(info.lockHeld, "锁应该已释放");
    }

    /**
     * 模拟时间流逝（直接创建状态文件）
     */
    private void simulateTimePassed(Duration duration) {
        try {
            Path stateFile = memoryDir.resolve(".consolidation_state");
            
            // 获取当前状态信息
            ConsolidationGate.ConsolidationInfo info = gate.getInfo();
            
            // 计算过去的时间
            Instant oldTime = Instant.now().minus(duration);
            
            // 直接创建状态文件
            String json = """
                {
                  "lastConsolidatedAt" : "%s",
                  "recentSessionIds" : [],
                  "newSessionCountSinceLast" : %d,
                  "consecutiveFailures" : %d
                }
                """.formatted(oldTime.toString(), info.newSessionCount, info.consecutiveFailures);
            
            Files.writeString(stateFile, json);
            
            // 重新加载状态
            gate = new ConsolidationGate(memoryDir);
        } catch (IOException e) {
            fail("模拟时间流逝失败：" + e.getMessage());
        }
    }
}
