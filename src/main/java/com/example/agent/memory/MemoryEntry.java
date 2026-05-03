package com.example.agent.memory;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记忆条目
 * 
 * 支持 frontmatter 元数据，包含可变字段用于激活策略追踪
 * 
 * 设计哲学：
 * - 不保留 importance 和 confidence 字段
 * - 不让 LLM 给自己的输出打分（不稳定且无意义）
 * - 相关性靠内容匹配，不靠 LLM 编的数字
 */
public class MemoryEntry {

    // 不可变字段（创建时确定）
    private final String id;
    private final Instant createdAt;
    
    // 可变字段（运行时更新）
    private String content;
    private MemoryType type;
    private Set<String> tags;
    private volatile Instant lastAccessed;
    private volatile Instant lastUpdated;
    private AtomicInteger accessCount;
    private String scope;

    public MemoryEntry(String id, String content, MemoryType type, Set<String> tags) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.tags = tags;
        this.createdAt = Instant.now();
        this.lastAccessed = Instant.now();
        this.lastUpdated = Instant.now();
        this.accessCount = new AtomicInteger(0);
        this.scope = "project";
    }

    public enum MemoryType {
        USER_PREFERENCE("User Preference"),
        FEEDBACK("Feedback"),
        PROJECT_CONTEXT("Project Context"),
        REFERENCE("Reference");

        private final String displayName;

        MemoryType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Getters
    public String getId() { return id; }
    public String getContent() { return content; }
    public MemoryType getType() { return type; }
    public Set<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessed() { return lastAccessed; }
    public Instant getLastUpdated() { return lastUpdated; }
    public int getAccessCount() { return accessCount.get(); }
    public String getScope() { return scope; }

    // Setters for mutable fields
    public void setContent(String content) {
        this.content = content;
        this.lastUpdated = Instant.now();
    }

    public void setType(MemoryType type) {
        this.type = type;
        this.lastUpdated = Instant.now();
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
        this.lastUpdated = Instant.now();
    }

    public void setScope(String scope) {
        this.scope = scope;
        this.lastUpdated = Instant.now();
    }

    /**
     * 记录访问（线程安全）
     */
    public void recordAccess() {
        this.lastAccessed = Instant.now();
        this.accessCount.incrementAndGet();
    }

    /**
     * 计算相关性评分（委托给 RelevanceScorer）
     */
    public double getRelevanceScore() {
        return RelevanceScorer.calculateRelevanceScore(this);
    }

    /**
     * 计算与查询的相关性（委托给 RelevanceScorer）
     */
    public double calculateRelevance(String query) {
        return RelevanceScorer.calculateRelevance(this, query);
    }
}
