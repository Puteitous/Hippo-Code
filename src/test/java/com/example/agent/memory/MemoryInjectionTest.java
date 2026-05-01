package com.example.agent.memory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 记忆注入功能集成测试
 * 
 * 验收场景：
 * 1. getIndexText(maxEntries) 按重要性排序并限制条数
 * 2. getRelevantMemoriesAsPrompt() 基于关键词匹配返回相关记忆
 * 3. 空索引处理
 * 4. 低相关性阈值过滤
 */
@DisplayName("记忆注入功能集成测试")
class MemoryInjectionTest {

    @TempDir
    Path tempDir;

    private MemoryStore store;
    private MemoryToolSandbox sandbox;

    @BeforeEach
    void setUp() {
        Path memoryDir = tempDir.resolve(".hippo/memory");
        try {
            Files.createDirectories(memoryDir);
        } catch (IOException e) {
            fail("创建目录失败：" + e.getMessage());
        }
        sandbox = new MemoryToolSandbox(memoryDir);
        store = new MemoryStore(sandbox);
    }

    @Test
    @DisplayName("getIndexText(maxEntries) 按重要性排序并限制条数")
    void testGetIndexTextWithLimit() {
        // 添加 10 条记忆，重要性不同
        for (int i = 0; i < 10; i++) {
            MemoryEntry entry = new MemoryEntry("test-" + i, "记忆 " + i, MemoryEntry.MemoryType.FACT, Set.of("tag" + i), 0.8);
            entry.setImportance((i + 1) * 0.1);
            store.add(entry);
        }

        // 限制返回 5 条
        String indexText = store.getIndexText(5);

        assertNotNull(indexText);
        assertTrue(indexText.contains("长期记忆索引"));
        assertTrue(indexText.contains("共 10 条记忆"));
        assertTrue(indexText.contains("以下展示最重要的 5 条"));
        
        // 验证只包含 5 条记忆
        int count = indexText.split("\n- ").length - 1;
        assertEquals(5, count);
        
        // 验证重要性最高的排在前面
        assertTrue(indexText.indexOf("记忆 9") < indexText.indexOf("记忆 8"));
    }

    @Test
    @DisplayName("getRelevantMemoriesAsPrompt() 基于关键词匹配")
    void testGetRelevantMemoriesAsPrompt() {
        // 添加不同类型的记忆
        MemoryEntry springEntry = new MemoryEntry("spring-core", "Spring Core 配置", MemoryEntry.MemoryType.TECHNICAL_CONTEXT, Set.of("spring", "java", "backend"), 0.9);
        springEntry.setContent("# Spring Core 配置\n\nSpring Boot 自动配置原理...");
        store.add(springEntry);

        MemoryEntry reactEntry = new MemoryEntry("react-hooks", "React Hooks 使用", MemoryEntry.MemoryType.TECHNICAL_CONTEXT, Set.of("react", "javascript", "frontend"), 0.8);
        reactEntry.setContent("# React Hooks\n\nuseState 和 useEffect 的使用...");
        store.add(reactEntry);

        MemoryEntry dockerEntry = new MemoryEntry("docker-deploy", "Docker 部署", MemoryEntry.MemoryType.DECISION, Set.of("docker", "devops"), 0.7);
        dockerEntry.setContent("# Docker 部署\n\n使用 docker-compose 部署...");
        store.add(dockerEntry);

        // 搜索与 Spring 相关的记忆
        String context = "我想了解 Spring Boot 的自动配置原理，以及如何配置数据源";
        String prompt = store.getRelevantMemoriesAsPrompt(context);

        assertNotNull(prompt);
        assertTrue(prompt.contains("相关历史记忆"));
        assertTrue(prompt.contains("Spring Core 配置"));
        assertFalse(prompt.contains("React Hooks"));
        assertFalse(prompt.contains("Docker 部署"));
    }

    @Test
    @DisplayName("空索引处理")
    void testEmptyIndex() {
        String indexText = store.getIndexText(10);
        assertEquals("", indexText);

        String prompt = store.getRelevantMemoriesAsPrompt("测试上下文");
        assertEquals("", prompt);
    }

    @Test
    @DisplayName("低相关性阈值过滤")
    void testRelevanceThresholdFiltering() {
        // 添加不相关的记忆
        MemoryEntry entry = new MemoryEntry("unrelated", "不相关的记忆", MemoryEntry.MemoryType.FACT, Set.of("random", "test"), 0.1);
        entry.setContent("# 不相关的内容\n\n这与任何搜索都无关");
        store.add(entry);

        // 搜索完全不相关的关键词
        String context = "Spring Boot React Docker Kubernetes";
        String prompt = store.getRelevantMemoriesAsPrompt(context);

        assertEquals("", prompt);
    }

    @Test
    @DisplayName("类型图标和重要性星星正确")
    void testTypeIconsAndImportanceStars() {
        MemoryEntry entry = new MemoryEntry("test-entry", "测试记忆", MemoryEntry.MemoryType.LESSON_LEARNED, Set.of("lesson"), 0.9);
        entry.setImportance(0.8);
        store.add(entry);

        String indexText = store.getIndexText(10);

        assertTrue(indexText.contains("💡"));
        assertTrue(indexText.contains("⭐⭐⭐⭐"));
    }
}
