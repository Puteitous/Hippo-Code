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
 * 1. getIndexText(maxEntries) 按最后更新时间排序并限制条数
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
    @DisplayName("getIndexText(maxEntries) 按最后更新时间排序并限制条数")
    void testGetIndexTextWithLimit() {
        // 添加 10 条记忆
        for (int i = 0; i < 10; i++) {
            MemoryEntry entry = new MemoryEntry("test-" + i, "记忆 " + i, MemoryEntry.MemoryType.USER_PREFERENCE, Set.of("tag" + i));
            store.add(entry);
            // 添加小延迟确保不同的更新时间
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 限制返回 5 条
        String indexText = store.getIndexText(5);

        assertNotNull(indexText);
        assertTrue(indexText.contains("长期记忆索引"));
        assertTrue(indexText.contains("共 10 条记忆"));
        assertTrue(indexText.contains("以下展示最近的 5 条"));
        
        // 验证只包含 5 条记忆
        int count = indexText.split("\n- ").length - 1;
        assertEquals(5, count);
    }

    @Test
    @DisplayName("getRelevantMemoriesAsPrompt() 基于关键词匹配")
    void testGetRelevantMemoriesAsPrompt() {
        // 添加不同类型的记忆
        MemoryEntry springEntry = new MemoryEntry("spring-core", "Spring Core 配置", MemoryEntry.MemoryType.PROJECT_CONTEXT, Set.of("spring", "java", "backend"));
        springEntry.setContent("# Spring Core 配置\n\nSpring Boot 自动配置原理...");
        store.add(springEntry);

        MemoryEntry reactEntry = new MemoryEntry("react-hooks", "React Hooks 使用", MemoryEntry.MemoryType.REFERENCE, Set.of("react", "javascript", "frontend"));
        reactEntry.setContent("# React Hooks\n\nuseState 和 useEffect 的使用...");
        store.add(reactEntry);

        MemoryEntry dockerEntry = new MemoryEntry("docker-deploy", "Docker 部署", MemoryEntry.MemoryType.FEEDBACK, Set.of("docker", "devops"));
        dockerEntry.setContent("# Docker 部署\n\n使用 docker-compose 部署...");
        store.add(dockerEntry);

        // 搜索与 Spring 相关的记忆
        String context = "我想了解 Spring Boot 的自动配置原理";
        String prompt = store.getRelevantMemoriesAsPrompt(context);

        assertNotNull(prompt);
        assertTrue(prompt.contains("相关历史记忆"));
        assertTrue(prompt.contains("Spring Core 配置"));
        // Spring 记忆应该有最高的相关性分数
        int springIndex = prompt.indexOf("Spring Core 配置");
        int reactIndex = prompt.indexOf("React Hooks");
        int dockerIndex = prompt.indexOf("Docker 部署");
        // Spring 应该排在最前面
        if (reactIndex > 0 || dockerIndex > 0) {
            assertTrue(springIndex < reactIndex || springIndex < dockerIndex);
        }
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
        MemoryEntry entry = new MemoryEntry("unrelated", "不相关的记忆", MemoryEntry.MemoryType.USER_PREFERENCE, Set.of("random", "test"));
        entry.setContent("# 不相关的内容\n\n这与任何搜索都无关");
        store.add(entry);

        // 搜索完全不相关的关键词
        String context = "Spring Boot React Docker Kubernetes";
        String prompt = store.getRelevantMemoriesAsPrompt(context);

        assertEquals("", prompt);
    }

    @Test
    @DisplayName("类型图标正确")
    void testTypeIcons() {
        MemoryEntry entry = new MemoryEntry("test-entry", "测试记忆", MemoryEntry.MemoryType.FEEDBACK, Set.of("lesson"));
        store.add(entry);

        waitForIndexUpdate();
        
        String indexText = store.getIndexText(10);

        assertTrue(indexText.contains("✅"), "应该包含 FEEDBACK 类型图标");
    }

    private void waitForIndexUpdate() {
        try {
            Thread.sleep(300); // 等待异步索引更新
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
