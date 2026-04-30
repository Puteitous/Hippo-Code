package com.example.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 三重门触发机制
 * 
 * 核心目标：回答"现在是不是整理长期记忆的好时机？"
 * 
 * 三重门：
 * 1. 时间门：距离上次整理至少 24 小时
 * 2. 会话门：至少累积了 3 个新会话
 * 3. 锁门：获取进程锁（防止并发整理）
 */
public class ConsolidationGate {

    private static final Logger logger = LoggerFactory.getLogger(ConsolidationGate.class);

    // 门 1 阈值：距离上次整理至少 24 小时
    private static final long MIN_HOURS = 24;
    // 门 2 阈值：至少累积了 3 个新会话
    private static final int MIN_SESSIONS = 3;
    // LRU 列表最大容量
    private static final int MAX_RECENT_SESSIONS = 200;
    // 连续失败阈值：超过此次数暂停触发
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final Path stateFilePath;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    // 状态字段（从 stateFilePath 反序列化）
    private volatile Instant lastConsolidatedAt = Instant.MIN;
    private final LinkedBlockingDeque<String> recentSessionIds = new LinkedBlockingDeque<>(MAX_RECENT_SESSIONS);
    private final AtomicInteger newSessionCountSinceLast = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // 锁状态
    private final AtomicBoolean lockHeld = new AtomicBoolean(false);

    /**
     * 状态持久化对象
     */
    private static class ConsolidationState {
        public Instant lastConsolidatedAt;
        public List<String> recentSessionIds;
        public int newSessionCountSinceLast;
        public int consecutiveFailures;

        public ConsolidationState() {}

        public ConsolidationState(ConsolidationGate gate) {
            this.lastConsolidatedAt = gate.lastConsolidatedAt;
            this.recentSessionIds = new ArrayList<>(gate.recentSessionIds);
            this.newSessionCountSinceLast = gate.newSessionCountSinceLast.get();
            this.consecutiveFailures = gate.consecutiveFailures.get();
        }
    }

    public ConsolidationGate(Path memoryDir) {
        this.stateFilePath = memoryDir.resolve(".consolidation_state");
        loadState();
    }

    /**
     * 三重门检查
     */
    public boolean shouldConsolidate() {
        // 检查连续失败次数
        if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            logger.error("连续 {} 次整理失败，暂停触发", consecutiveFailures.get());
            return false;
        }

        // 门 1：时间检查（纯内存比较，成本最低）
        Duration sinceLastConsolidation = Duration.between(lastConsolidatedAt, Instant.now());
        if (sinceLastConsolidation.toHours() < MIN_HOURS) {
            logger.debug("门 1 拦截：距离上次整理仅 {} 小时", sinceLastConsolidation.toHours());
            return false;
        }

        // 门 2：会话数量检查
        int newSessions = newSessionCountSinceLast.get();
        if (newSessions < MIN_SESSIONS) {
            logger.debug("门 2 拦截：新会话数 {} < {}", newSessions, MIN_SESSIONS);
            return false;
        }

        // 门 3：获取进程锁
        if (!tryAcquireLock()) {
            logger.debug("门 3 拦截：获取锁失败（可能正在整理）");
            return false;
        }

        logger.info("三重门全部通过，触发跨会话整理（新会话数：{}，距上次：{} 小时）",
            newSessions, sinceLastConsolidation.toHours());
        return true;
    }

    /**
     * 注册新会话
     */
    public void registerSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        // 检查是否已经注册过
        if (recentSessionIds.contains(sessionId)) {
            return;
        }

        // 添加到 LRU 列表（如果满了，移除最旧的）
        if (recentSessionIds.size() >= MAX_RECENT_SESSIONS) {
            recentSessionIds.pollFirst();
        }
        recentSessionIds.offerLast(sessionId);

        // 增加新会话计数
        newSessionCountSinceLast.incrementAndGet();

        // 持久化状态
        saveState();

        logger.debug("注册新会话：{}（当前新会话数：{}）", sessionId, newSessionCountSinceLast.get());
    }

    /**
     * 标记会话为已处理
     */
    public void markSessionAsProcessed(String sessionId) {
        // 从 LRU 列表中移除
        recentSessionIds.remove(sessionId);
        
        // 减少新会话计数
        int remaining = newSessionCountSinceLast.decrementAndGet();
        if (remaining < 0) {
            newSessionCountSinceLast.set(0);
        }

        // 持久化状态
        saveState();

        logger.debug("标记会话为已处理：{}（剩余新会话数：{}）", sessionId, remaining);
    }

    /**
     * 标记整理完成
     */
    public void markConsolidationComplete() {
        // 更新最后整理时间
        lastConsolidatedAt = Instant.now();

        // 重置新会话计数
        newSessionCountSinceLast.set(0);

        // 清空 LRU 列表
        recentSessionIds.clear();

        // 重置失败计数
        consecutiveFailures.set(0);

        // 释放锁
        releaseLock();

        // 持久化状态
        saveState();

        logger.info("整理完成，状态已重置");
    }

    /**
     * 标记整理失败
     */
    public void markConsolidationFailed() {
        // 增加失败计数
        int failures = consecutiveFailures.incrementAndGet();

        // 释放锁（让下次检查能通过）
        releaseLock();

        // 不更新 lastConsolidatedAt
        // 不重置 newSessionCountSinceLast

        // 持久化状态
        saveState();

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            logger.error("连续 {} 次整理失败，暂停触发直到手动重置", failures);
        } else {
            logger.warn("整理失败（连续失败次数：{}）", failures);
        }
    }

    /**
     * 获取状态信息（用于监控和测试）
     */
    public ConsolidationInfo getInfo() {
        return new ConsolidationInfo(
            lastConsolidatedAt,
            recentSessionIds.size(),
            newSessionCountSinceLast.get(),
            consecutiveFailures.get(),
            lockHeld.get()
        );
    }

    /**
     * 手动重置状态（用于测试或管理员操作）
     */
    public void reset() {
        lastConsolidatedAt = Instant.MIN;
        newSessionCountSinceLast.set(0);
        recentSessionIds.clear();
        consecutiveFailures.set(0);
        releaseLock();
        saveState();
        logger.info("状态已手动重置");
    }

    /**
     * 仅释放锁（用于测试失败恢复场景）
     */
    void releaseLockOnly() {
        releaseLock();
        saveState();
    }

    // ========== 私有方法 ==========

    /**
     * 尝试获取锁
     */
    private boolean tryAcquireLock() {
        if (lockHeld.compareAndSet(false, true)) {
            logger.debug("获取整理锁成功");
            return true;
        }
        return false;
    }

    /**
     * 释放锁
     */
    private void releaseLock() {
        lockHeld.set(false);
        logger.debug("释放整理锁");
    }

    /**
     * 加载状态
     */
    private void loadState() {
        if (!Files.exists(stateFilePath)) {
            logger.debug("状态文件不存在，使用默认状态");
            return;
        }

        try {
            String json = Files.readString(stateFilePath);
            ConsolidationState state = objectMapper.readValue(json, ConsolidationState.class);

            lastConsolidatedAt = state.lastConsolidatedAt != null ? state.lastConsolidatedAt : Instant.MIN;
            newSessionCountSinceLast.set(state.newSessionCountSinceLast);
            consecutiveFailures.set(state.consecutiveFailures);

            if (state.recentSessionIds != null) {
                recentSessionIds.clear();
                for (String sessionId : state.recentSessionIds) {
                    recentSessionIds.offerLast(sessionId);
                }
            }

            logger.info("加载状态成功（最后整理：{}，新会话数：{}，失败次数：{}）",
                lastConsolidatedAt, newSessionCountSinceLast.get(), consecutiveFailures.get());
        } catch (IOException e) {
            logger.warn("加载状态失败，使用默认状态", e);
        }
    }

    /**
     * 保存状态
     */
    private void saveState() {
        try {
            ConsolidationState state = new ConsolidationState(this);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);

            // 原子写入
            Path tempFile = stateFilePath.resolveSibling(stateFilePath.getFileName() + ".tmp");
            Files.writeString(tempFile, json, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempFile, stateFilePath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("保存状态失败", e);
        }
    }

    /**
     * 状态信息（用于监控和测试）
     */
    public static class ConsolidationInfo {
        public final Instant lastConsolidatedAt;
        public final int recentSessionCount;
        public final int newSessionCount;
        public final int consecutiveFailures;
        public final boolean lockHeld;

        public ConsolidationInfo(Instant lastConsolidatedAt, int recentSessionCount,
                                 int newSessionCount, int consecutiveFailures, boolean lockHeld) {
            this.lastConsolidatedAt = lastConsolidatedAt;
            this.recentSessionCount = recentSessionCount;
            this.newSessionCount = newSessionCount;
            this.consecutiveFailures = consecutiveFailures;
            this.lockHeld = lockHeld;
        }
    }
}
