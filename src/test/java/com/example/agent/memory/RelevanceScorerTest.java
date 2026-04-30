package com.example.agent.memory;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RelevanceScorer 单元测试
 */
class RelevanceScorerTest {

    @Test
    void testCalculateRelevance() {
        Set<String> tags = new HashSet<>();
        tags.add("spring");
        tags.add("security");

        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Spring Security JWT configuration with 24h token expiry",
            MemoryEntry.MemoryType.DECISION,
            tags,
            0.8
        );

        double relevance = RelevanceScorer.calculateRelevance(entry, "spring security");
        assertTrue(relevance > 0.3, "标签匹配应该有较高分");

        double relevance2 = RelevanceScorer.calculateRelevance(entry, "JWT token");
        assertTrue(relevance2 > 0, "内容匹配应该有分");

        double relevance3 = RelevanceScorer.calculateRelevance(entry, "");
        assertEquals(0.0, relevance3, 0.001, "空查询应该得 0 分");

        double relevance4 = RelevanceScorer.calculateRelevance(null, "test");
        assertEquals(0.0, relevance4, 0.001, "null 条目应该得 0 分");
    }

    @Test
    void testCalculateTimeDecay() {
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.5
        );

        double decay = RelevanceScorer.calculateTimeDecay(entry);
        assertTrue(decay > 0.9, "刚创建的条目时间衰减应该接近 1.0");
    }

    @Test
    void testCalculateRelevanceScore() {
        MemoryEntry entry1 = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content 1",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.9,
            0.9
        );

        MemoryEntry entry2 = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Content 2",
            MemoryEntry.MemoryType.FACT,
            new HashSet<>(),
            0.3,
            0.3
        );

        double score1 = RelevanceScorer.calculateRelevanceScore(entry1);
        double score2 = RelevanceScorer.calculateRelevanceScore(entry2);

        assertTrue(score1 > score2, "高重要性/高置信度的条目应该有更高的综合评分");
    }

    @Test
    void testRankByRelevance() {
        Set<String> tags1 = new HashSet<>();
        tags1.add("java");
        MemoryEntry entry1 = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Java best practices",
            MemoryEntry.MemoryType.LESSON_LEARNED,
            tags1,
            0.9
        );

        Set<String> tags2 = new HashSet<>();
        tags2.add("python");
        MemoryEntry entry2 = new MemoryEntry(
            UUID.randomUUID().toString(),
            "Python tips",
            MemoryEntry.MemoryType.LESSON_LEARNED,
            tags2,
            0.8
        );

        List<MemoryEntry> entries = List.of(entry1, entry2);

        // 按"java"相关性排序
        List<MemoryEntry> ranked = RelevanceScorer.rankByRelevance(entries, "java");

        assertEquals(2, ranked.size());
        assertEquals(entry1, ranked.get(0), "Java 相关的条目应该排在前面");
    }

    @Test
    void testRankByRelevance_EmptyList() {
        List<MemoryEntry> ranked = RelevanceScorer.rankByRelevance(List.of(), "test");
        assertTrue(ranked.isEmpty());
    }

    @Test
    void testNullSafety() {
        assertEquals(0.0, RelevanceScorer.calculateRelevance(null, "test"), 0.001);
        assertEquals(0.0, RelevanceScorer.calculateRelevanceScore(null), 0.001);
        assertEquals(0.0, RelevanceScorer.calculateTimeDecay(null), 0.001);
    }
}
