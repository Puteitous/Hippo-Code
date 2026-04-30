package com.example.agent.memory;

import com.example.agent.testutil.MockLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MemoryStoreTest {

    @TempDir
    Path tempDir;

    private MockLlmClient mockLlmClient;
    private MemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        mockLlmClient = new MockLlmClient();
        memoryStore = new MemoryStore(mockLlmClient, tempDir.toString());
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testParseMemoryFileWithCorrectStructure() throws IOException {
        String content = "# MEMORY.md - Hippo Agent 长期记忆\n\n" +
            "> 本文件由 Auto Dream 自动整理，也可手动编辑\n\n" +
            "---\n\n" +
            "## 用户偏好\n\n" +
            "### 偏好使用 Java 17 (java, version)\n\n" +
            "### 偏好使用 VS Code (editor, ide)\n\n" +
            "## 技术上下文\n\n" +
            "### 使用 Spring Boot 3.x (spring, framework)\n\n" +
            "### 使用 JUnit 5 进行测试 (testing, junit)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        assertEquals(4, store.size(), "应该正确解析 4 条记忆");

        List<MemoryEntry> memories = store.getAllMemories();
        List<MemoryEntry> userPrefs = memories.stream()
            .filter(m -> m.getType() == MemoryEntry.MemoryType.USER_PREFERENCE)
            .collect(Collectors.toList());

        assertEquals(2, userPrefs.size(), "应该有 2 条用户偏好记忆");
        assertTrue(userPrefs.stream().anyMatch(m -> m.getContent().contains("Java 17")));
        assertTrue(userPrefs.stream().anyMatch(m -> m.getContent().contains("VS Code")));

        List<MemoryEntry> techContexts = memories.stream()
            .filter(m -> m.getType() == MemoryEntry.MemoryType.TECHNICAL_CONTEXT)
            .collect(Collectors.toList());

        assertEquals(2, techContexts.size(), "应该有 2 条技术上下文记忆");
    }

    @Test
    void testParseMemoryFileIgnoresMalformedHeaders() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 项目上下文\n\n" +
            "### 项目使用微服务架构 (architecture)\n\n" +
            "### ## 错误的标题格式 (test)\n\n" +
            "### 正常的记忆条目 (normal)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        assertEquals(3, store.size(), "应该解析 3 条记忆，包括清理后的错误标题");

        List<MemoryEntry> memories = store.getAllMemories();
        MemoryEntry malformedEntry = memories.stream()
            .filter(m -> m.getContent().contains("错误的标题格式"))
            .findFirst()
            .orElse(null);

        assertNotNull(malformedEntry);
        assertFalse(malformedEntry.getContent().startsWith("##"), "应该清理掉多余的 ## 前缀");
    }

    @Test
    void testProcessDreamResultDeduplicates() {
        memoryStore.addPendingMemory("候选记忆 1");
        memoryStore.addPendingMemory("候选记忆 2");
        memoryStore.addPendingMemory("候选记忆 3");

        mockLlmClient.enqueueSuccessResponse(
            "### 用户偏好使用 Java (java, language)\n" +
            "### 技术上下文使用 Spring Boot (spring, framework)\n" +
            "### 用户偏好使用 Java (java, language)\n" +
            "### 项目上下文微服务架构 (architecture)"
        );

        memoryStore.triggerAutoDream();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<MemoryEntry> memories = memoryStore.getAllMemories();
        long javaPreferenceCount = memories.stream()
            .filter(m -> m.getContent().contains("用户偏好使用 Java"))
            .count();

        assertEquals(1, javaPreferenceCount, "相同的记忆应该只保留一条");
        assertEquals(3, memories.size(), "应该有 3 条不重复的记忆");
    }

    @Test
    void testProcessDreamResultCleansMalformedLlmOutput() {
        memoryStore.addPendingMemory("候选记忆 1");
        memoryStore.addPendingMemory("候选记忆 2");
        memoryStore.addPendingMemory("候选记忆 3");

        mockLlmClient.enqueueSuccessResponse(
            "### ## 技术上下文 (java)\n" +
            "### ### 用户偏好 (editor)\n" +
            "### 正常的记忆 (normal)"
        );

        memoryStore.triggerAutoDream();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<MemoryEntry> memories = memoryStore.getAllMemories();
        assertEquals(3, memories.size(), "应该解析 3 条记忆");

        memories.forEach(memory -> {
            assertFalse(memory.getContent().startsWith("##"), "不应该以 ## 开头");
            assertFalse(memory.getContent().startsWith("###"), "不应该以 ### 开头");
        });
    }

    @Test
    void testSaveAndLoadRoundTrip() throws IOException {
        memoryStore.addPendingMemory("记忆 1");
        memoryStore.addPendingMemory("记忆 2");
        memoryStore.addPendingMemory("记忆 3");

        mockLlmClient.enqueueSuccessResponse(
            "### 用户偏好使用暗色主题 (editor, theme)\n" +
            "### 技术上下文使用 PostgreSQL (database, sql)\n"
        );

        memoryStore.triggerAutoDream();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MemoryStore reloadedStore = new MemoryStore(mockLlmClient, tempDir.toString());

        assertEquals(2, reloadedStore.size(), "重新加载后应该有 2 条记忆");

        List<MemoryEntry> memories = reloadedStore.getAllMemories();
        assertTrue(memories.stream().anyMatch(m ->
            m.getType() == MemoryEntry.MemoryType.USER_PREFERENCE &&
            m.getContent().contains("暗色主题")));
        assertTrue(memories.stream().anyMatch(m ->
            m.getType() == MemoryEntry.MemoryType.TECHNICAL_CONTEXT &&
            m.getContent().contains("PostgreSQL")));
    }

    @Test
    void testExtractTagsFromHeader() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 经验教训\n\n" +
            "### 踩坑记录：数据库连接池配置 (database, connection-pool, production)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        assertEquals(1, store.size());
        MemoryEntry entry = store.getAllMemories().get(0);

        Set<String> tags = entry.getTags();
        assertEquals(3, tags.size());
        assertTrue(tags.contains("database"));
        assertTrue(tags.contains("connection-pool"));
        assertTrue(tags.contains("production"));
    }

    @Test
    void testSearchRelevantMemories() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 技术上下文\n\n" +
            "### 使用 Spring Boot 3.x (spring, framework, java)\n\n" +
            "### 使用 PostgreSQL 数据库 (database, sql, postgres)\n\n" +
            "## 用户偏好\n\n" +
            "### 偏好使用 IntelliJ IDEA (editor, ide, jetbrains)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        List<MemoryEntry> results = store.searchRelevant("spring framework", 5);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getContent().contains("Spring Boot"));

        List<MemoryEntry> databaseResults = store.searchRelevant("database postgres", 5);
        assertTrue(databaseResults.stream().anyMatch(m -> m.getContent().contains("PostgreSQL")));
    }

    @Test
    void testGetRelevantMemoriesAsPrompt() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 技术上下文\n\n" +
            "### 使用 Spring Boot (spring, framework)\n\n" +
            "### 使用 JUnit 5 (testing, junit)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        String prompt = store.getRelevantMemoriesAsPrompt("spring");
        assertNotNull(prompt);
        assertTrue(prompt.contains("相关记忆"));
        assertTrue(prompt.contains("Spring Boot"));
    }

    @Test
    void testSearchRelevanceWithDifferentQueries() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 技术上下文\n\n" +
            "### 使用 Spring Boot (spring, framework)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        List<MemoryEntry> springResults = store.searchRelevant("spring framework", 5);
        assertFalse(springResults.isEmpty());
        assertTrue(springResults.get(0).getContent().contains("Spring Boot"));

        List<MemoryEntry> unrelatedResults = store.searchRelevant("kubernetes docker", 5);
        assertTrue(unrelatedResults.isEmpty() || unrelatedResults.size() <= springResults.size(),
            "不相关查询的结果应该少于或等于相关查询");
    }

    @Test
    void testParseTypeFromString() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 用户偏好\n\n" +
            "### 偏好设置 1 (tag1)\n\n" +
            "## 技术上下文\n\n" +
            "### 技术设置 2 (tag2)\n\n" +
            "## 关键决策\n\n" +
            "### 决策内容 3 (tag3)\n\n" +
            "## 经验教训\n\n" +
            "### 教训内容 4 (tag4)\n\n" +
            "## 项目上下文\n\n" +
            "### 项目信息 5 (tag5)\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());

        List<MemoryEntry> memories = store.getAllMemories();
        assertEquals(5, memories.size());

        assertEquals(MemoryEntry.MemoryType.USER_PREFERENCE, memories.get(0).getType());
        assertEquals(MemoryEntry.MemoryType.TECHNICAL_CONTEXT, memories.get(1).getType());
        assertEquals(MemoryEntry.MemoryType.DECISION, memories.get(2).getType());
        assertEquals(MemoryEntry.MemoryType.LESSON_LEARNED, memories.get(3).getType());
        assertEquals(MemoryEntry.MemoryType.PROJECT_CONTEXT, memories.get(4).getType());
    }

    @Test
    void testAddPendingMemoryFiltersBlank() {
        memoryStore.addPendingMemory("");
        memoryStore.addPendingMemory("   ");
        memoryStore.addPendingMemory(null);
        memoryStore.addPendingMemory("有效的记忆");

        assertEquals(1, memoryStore.getPendingCount());
    }

    @Test
    void testTriggerAutoDreamRequiresMinimumCandidates() {
        memoryStore.addPendingMemory("记忆 1");
        memoryStore.addPendingMemory("记忆 2");

        assertEquals(2, memoryStore.getPendingCount());

        memoryStore.triggerAutoDream();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(2, memoryStore.getPendingCount(), "少于 3 条候选记忆时不应触发 dream");
        assertEquals(0, memoryStore.size(), "不应该有任何记忆");
    }

    @Test
    void testSaveIsSynchronized() throws IOException, InterruptedException {
        memoryStore.addPendingMemory("记忆 1");
        memoryStore.addPendingMemory("记忆 2");
        memoryStore.addPendingMemory("记忆 3");

        mockLlmClient.enqueueSuccessResponse(
            "### 技术上下文使用多线程 (concurrency, threading)\n"
        );

        Thread thread1 = new Thread(() -> {
            memoryStore.triggerAutoDream();
        });

        thread1.start();
        
        int waitCount = 0;
        Path memoryFile = tempDir.resolve("MEMORY.md");
        while (!Files.exists(memoryFile) && waitCount < 50) {
            Thread.sleep(100);
            waitCount++;
        }

        assertTrue(Files.exists(memoryFile), "MEMORY.md 文件应该被创建");

        String content = Files.readString(memoryFile);
        assertNotNull(content);
        assertFalse(content.isEmpty(), "文件内容不应该为空");
        assertTrue(content.contains("技术上下文"), "文件应该包含类型标题");
        assertTrue(content.contains("多线程"), "文件应该包含记忆内容");
    }

    @Test
    void testEmptyMemoryFileDoesNotThrow() {
        assertDoesNotThrow(() -> {
            MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());
            assertEquals(0, store.size());
        });
    }

    @Test
    void testMemoryFileWithOnlyHeaders() throws IOException {
        String content = "# MEMORY.md\n\n" +
            "## 用户偏好\n\n" +
            "## 技术上下文\n\n";

        Path memoryFile = tempDir.resolve("MEMORY.md");
        Files.writeString(memoryFile, content);

        MemoryStore store = new MemoryStore(mockLlmClient, tempDir.toString());
        assertEquals(0, store.size(), "只有标题没有内容时应该解析出 0 条记忆");
    }
}
