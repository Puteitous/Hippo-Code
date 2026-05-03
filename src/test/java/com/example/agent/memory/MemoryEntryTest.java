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
            MemoryEntry.MemoryType.USER_PREFERENCE,
            tags
        );

        assertNotNull(entry.getId());
        assertEquals("Test content", entry.getContent());
        assertEquals(MemoryEntry.MemoryType.USER_PREFERENCE, entry.getType());
        assertEquals("project", entry.getScope());
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getLastAccessed());
        assertEquals(0, entry.getAccessCount());
    }

    @Test
    void testSetters() throws InterruptedException {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Original content",
            MemoryEntry.MemoryType.USER_PREFERENCE,
            new HashSet<>()
        );

        Instant beforeUpdate = entry.getLastUpdated();
        Thread.sleep(10); // 确保时间戳有差异

        // 测试 setContent
        entry.setContent("Updated content");
        assertTrue(entry.getLastUpdated().isAfter(beforeUpdate));
        assertEquals("Updated content", entry.getContent());

        // 测试 setType
        entry.setType(MemoryEntry.MemoryType.FEEDBACK);
        assertEquals(MemoryEntry.MemoryType.FEEDBACK, entry.getType());

        // 测试 setTags
        Set<String> newTags = new HashSet<>();
        newTags.add("new-tag");
        entry.setTags(newTags);
        assertTrue(entry.getTags().contains("new-tag"));

        // 测试 setScope
        entry.setScope("user");
        assertEquals("user", entry.getScope());
    }

    @Test
    void testRecordAccess() {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content",
            MemoryEntry.MemoryType.USER_PREFERENCE,
            new HashSet<>()
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
            MemoryEntry.MemoryType.USER_PREFERENCE,
            new HashSet<>()
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
    void testCalculateRelevance() {
        Set<String> tags = new HashSet<>();
        tags.add("spring");
        tags.add("security");
        tags.add("jwt");

        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Spring Security JWT configuration with 24h token expiry",
            MemoryEntry.MemoryType.PROJECT_CONTEXT,
            tags
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
                new HashSet<>()
            );
            assertEquals(type, entry.getType());
        }

        assertEquals(4, types.length);
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.USER_PREFERENCE));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.FEEDBACK));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.PROJECT_CONTEXT));
        assertTrue(java.util.Arrays.asList(types).contains(MemoryEntry.MemoryType.REFERENCE));
    }

    @Test
    void testConcurrentUpdates() throws InterruptedException {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Initial",
            MemoryEntry.MemoryType.USER_PREFERENCE,
            new HashSet<>()
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
}
