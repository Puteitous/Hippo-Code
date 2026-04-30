package com.example.agent.memory;

/**
 * 相关性评分器
 * 
 * 负责计算记忆条目与查询的相关性评分
 * 将评分逻辑与数据模型分离，便于后续切换检索算法
 */
public class RelevanceScorer {

    private static final double TAG_MATCH_WEIGHT = 0.3;
    private static final double CONTENT_MATCH_WEIGHT = 0.1;
    private static final double IMPORTANCE_WEIGHT = 0.5;
    private static final double TIME_DECAY_FACTOR = 1.0;

    /**
     * 计算记忆条目与查询的相关性评分
     * 
     * @param entry 记忆条目
     * @param query 查询字符串
     * @return 相关性评分（0.0 - 1.0）
     */
    public static double calculateRelevance(MemoryEntry entry, String query) {
        if (entry == null || query == null || query.isBlank()) {
            return 0.0;
        }

        double relevance = 0;
        String queryLower = query.toLowerCase();
        String contentLower = entry.getContent().toLowerCase();

        // 标签匹配
        for (String tag : entry.getTags()) {
            if (queryLower.contains(tag.toLowerCase())) {
                relevance += TAG_MATCH_WEIGHT;
            }
        }

        // 内容匹配
        String[] queryWords = queryLower.split("\\s+");
        for (String word : queryWords) {
            if (contentLower.contains(word)) {
                relevance += CONTENT_MATCH_WEIGHT;
            }
        }

        // 重要性加权
        relevance += entry.getImportance() * IMPORTANCE_WEIGHT;

        return Math.min(relevance, 1.0);
    }

    /**
     * 计算记忆条目的时间衰减评分（用于激活策略）
     * 
     * @param entry 记忆条目
     * @return 时间衰减评分（0.0 - 1.0）
     */
    public static double calculateTimeDecay(MemoryEntry entry) {
        if (entry == null) {
            return 0.0;
        }

        long daysSinceAccess = java.time.Duration.between(
            entry.getLastAccessed(), 
            java.time.Instant.now()
        ).toDays();
        
        double timeDecay = TIME_DECAY_FACTOR / (TIME_DECAY_FACTOR + daysSinceAccess);
        return timeDecay;
    }

    /**
     * 计算记忆条目的综合相关性评分
     * 
     * 综合考虑重要性、置信度和时间衰减
     * 
     * @param entry 记忆条目
     * @return 综合评分（0.0 - 1.0）
     */
    public static double calculateRelevanceScore(MemoryEntry entry) {
        if (entry == null) {
            return 0.0;
        }

        double timeDecay = calculateTimeDecay(entry);
        return entry.getImportance() * entry.getConfidence() * timeDecay;
    }

    /**
     * 计算多个记忆条目的相关性评分并排序
     * 
     * @param entries 记忆条目列表
     * @param query 查询字符串
     * @return 按相关性降序排列的条目列表
     */
    public static java.util.List<MemoryEntry> rankByRelevance(
            java.util.List<MemoryEntry> entries, 
            String query) {
        
        if (entries == null || entries.isEmpty()) {
            return java.util.List.of();
        }

        return entries.stream()
            .sorted((e1, e2) -> Double.compare(
                calculateRelevance(e2, query),
                calculateRelevance(e1, query)
            ))
            .toList();
    }
}
