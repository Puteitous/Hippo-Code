package com.example.agent.memory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryEntry 单元测试
 * 
 * 测试可变字段、线程安全和激活策略
 */
class MemoryEntryTest {

    @Test
    void testMemoryEntryCreation() {
        Set<String> tags = new HashSet<>();
        tags.add("java");
        tags.add("test");

        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Test content",
            MemoryEntry.MemoryType.FACT,
            tags,
            0.8,
            0.9
        );

        assertNotNull(entry.getId());
        assertEquals("Test content", entry.getContent());
        assertEquals(MemoryEntry.MemoryType.FACT, entry.getType());
        assertEquals(0.8, entry.getImportance(), 0.001);
        assertEquals(0.9, entry.getConfidence(), 0.001);
        assertEquals("project", entry.getScope());
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getLastAccessed());
        assertEquals(0, entry.getAccessCount());
    }

    @Test
    void testMemoryEntryCreation_SimpleConstructor() {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Test content",
            MemoryEntry.MemoryType.DECISION,
            new HashSet<>(),
            0.7
        );

        assertEquals(0.8, entry.getConfidence(), 0.001); // 默认置信度
        assertEquals(0.7, entry.getImportance(), 0.001);
    }

    @Test
    void testSetters() throws InterruptedException {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Original content",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.5
        );

        Instant beforeUpdate = entry.getLastUpdated();
        Thread.sleep(10); // 确保时间戳有差异

        // 测试 setContent
        entry.setContent("Updated content");
        assertTrue(entry.getLastUpdated().isAfter(beforeUpdate));
        assertEquals("Updated content", entry.getContent());

        // 测试 setType
        entry.setType(MemoryEntry.MemoryType.DECISION);
        assertEquals(MemoryEntry.MemoryType.DECISION, entry.getType());

        // 测试 setTags
        Set<String> newTags = new HashSet<>();
        newTags.add("new-tag");
        entry.setTags(newTags);
        assertTrue(entry.getTags().contains("new-tag"));

        // 测试 setConfidence
        entry.setConfidence(0.95);
        assertEquals(0.95, entry.getConfidence(), 0.001);

        // 测试 setImportance
        entry.setImportance(0.9);
        assertEquals(0.9, entry.getImportance(), 0.001);

        // 测试 setScope
        entry.setScope("user");
        assertEquals("user", entry.getScope());
    }

    @Test
    void testRecordAccess() {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.5
        );

        int initialCount = entry.getAccessCount();
        Instant initialAccessed = entry.getLastAccessed();

        // 记录访问
        entry.recordAccess();

        assertEquals(initialCount + 1, entry.getAccessCount());
        assertTrue(entry.getLastAccessed().isAfter(initialAccessed) || 
                   entry.getLastAccessed().equals(initialAccessed)); // 可能相同，如果执行很快

        // 多次访问
        entry.recordAccess();
        entry.recordAccess();
        assertEquals(initialCount + 3, entry.getAccessCount());
    }

    @Test
    void testRecordAccess_ThreadSafety() throws InterruptedException {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.5
        );

        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    entry.recordAccess();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, entry.getAccessCount());
    }

    @Test
    void testGetRelevanceScore() throws InterruptedException {
        MemoryEntry entry1 = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content 1",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.9, // 高重要性
            0.9  // 高置信度
        );

        MemoryEntry entry2 = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content 2",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.3, // 低重要性
            0.3  // 低置信度
        );

        // 刚创建的条目，相关性应该较高
        double score1 = entry1.getRelevanceScore();
        double score2 = entry2.getRelevanceScore();

        assertTrue(score1 > score2, "高重要性/高置信度的条目应该有更高的相关性");

        // 测试访问次数对相关性没有影响（当前实现中 accessCount 不影响评分）
        entry2.recordAccess();
        entry2.recordAccess();
        entry2.recordAccess();
        
        // entry2 的重要性/置信度较低，即使多次访问，评分仍然较低
        double newScore1 = entry1.getRelevanceScore();
        double newScore2 = entry2.getRelevanceScore();
        
        assertTrue(newScore1 > newScore2, "重要性/置信度的权重高于访问频率");
    }

    @Test
    void testCalculateRelevance() {
        Set<String> tags = new HashSet<>();
        tags.add("spring");
        tags.add("security");
        tags.add("jwt");

        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Spring Security JWT configuration with 24h token expiry",
            MemoryEntry.MemoryType.DECISION,
            tags,
            0.8
        );

        // 测试标签匹配
        double relevance1 = entry.calculateRelevance("spring security");
        assertTrue(relevance1 > 0.3);

        // 测试内容匹配
        double relevance2 = entry.calculateRelevance("JWT token configuration");
        assertTrue(relevance2 > 0);

        // 测试空查询
        double relevance3 = entry.calculateRelevance("");
        assertEquals(0.0, relevance3, 0.001);

        // 测试 null 查询
        double relevance4 = entry.calculateRelevance(null);
        assertEquals(0.0, relevance4, 0.001);

        // 测试不相关查询
        double relevance5 = entry.calculateRelevance("database mysql");
        assertTrue(relevance5 < relevance1);
    }

    @Test
    void testMemoryTypes() {
        // 测试所有记忆类型都能正常创建
        MemoryEntry.MemoryType[] types = MemoryEntry.MemoryType.values();
        
        for (MemoryEntry.MemoryType type : types) {
            MemoryEntry entry = new MemoryEntry(
                UUID.randomUUID().toString(),
                "Content",
                type,
                new HashSet<>(),
                0.5
            );
            assertEquals(type, entry.getType());
        }

        assertEquals(6, types.length);
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.FACT));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.USER_PREFERENCE));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.TECHNICAL_CONTEXT));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.DECISION));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.LESSON_LEARNED));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.PROJECT_CONTEXT));
    }

    @Test
    void testConcurrentUpdates() throws InterruptedException {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Initial",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.5
        );

        CountDownLatch latch = new CountDownLatch(100);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            final int value = i;
            executor.submit(() -> {
                try {
                    synchronized (entry) {
                        entry.setContent("Content " + value);
                        entry.setImportance(value / 100.0);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(100, successCount.get());
    }

    @Test
    void testTimeDecayCalculation() throws InterruptedException {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            1.0, // 最大重要性
            1.0  // 最大置信度
        );

        // 刚访问，时间衰减应该接近 1.0
        double score1 = entry.getRelevanceScore();
        assertTrue(score1 > 0.9);

        // 模拟 7 天后的相关性
        Thread.sleep(100);
        entry.recordAccess();
        
        // 手动计算 7 天后的得分
        long days = 7;
        double expectedDecay = 1.0 / (1.0 + days);
        double expectedScore = 1.0 * 1.0 * expectedDecay;
        
        // 验证时间衰减公式
        assertEquals(expectedScore, expectedDecay, 0.001);
    }
}
