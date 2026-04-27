package com.example.agent.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryManagerTest {

    private SessionMemoryManager memoryManager;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        testSessionId = "test-session-" + System.currentTimeMillis();
        memoryManager = new SessionMemoryManager(testSessionId);
    }

    @AfterEach
    void tearDown() throws IOException {
        Path file = memoryManager.getMemoryFilePath();
        if (Files.exists(file)) {
            Files.delete(file);
            Path parent = file.getParent();
            while (parent != null && !Files.list(parent).findAny().isPresent()) {
                Files.delete(parent);
                parent = parent.getParent();
            }
        }
    }

    @Test
    void testWriteAndReadMemory() {
        String content = "# Test Memory\n\n## 关键决策\n\n- 使用时间戳作为 sessionId";

        memoryManager.write(content);

        assertTrue(memoryManager.exists());
        assertEquals(content, memoryManager.read());
    }

    @Test
    void testReadNonExistentMemory() {
        SessionMemoryManager emptyManager = new SessionMemoryManager("non-existent-" + System.nanoTime());
        assertNull(emptyManager.read());
        assertFalse(emptyManager.exists());
    }

    @Test
    void testAppendMemory() {
        String firstContent = "First content";
        String secondContent = "Second content";

        memoryManager.write(firstContent);
        memoryManager.append(secondContent);

        String result = memoryManager.read();
        assertTrue(result.contains(firstContent));
        assertTrue(result.contains(secondContent));
        assertTrue(result.contains("---"));
    }

    @Test
    void testAppendToNonExistentMemory() {
        SessionMemoryManager emptyManager = new SessionMemoryManager("new-session-" + System.nanoTime());
        String content = "New content";

        emptyManager.append(content);

        assertEquals(content, emptyManager.read());
    }

    @Test
    void testClearMemory() {
        memoryManager.write("Test content");
        assertTrue(memoryManager.exists());

        memoryManager.clear();
        assertFalse(memoryManager.exists());
    }

    @Test
    void testHasActualContentWithTemplateOnly() {
        String template = SessionMemoryManager.getDefaultMemoryTemplate();
        memoryManager.write(template);

        assertFalse(memoryManager.hasActualContent());
    }

    @Test
    void testHasActualContentWithRealContent() {
        String content = SessionMemoryManager.getDefaultMemoryTemplate() +
            "\n\n- 真正的决策内容";
        memoryManager.write(content);

        assertTrue(memoryManager.hasActualContent());
    }

    @Test
    void testHasActualContentWithEmpty() {
        assertFalse(memoryManager.hasActualContent());
    }

    @Test
    void testGetSessionId() {
        assertEquals(testSessionId, memoryManager.getSessionId());
    }

    @Test
    void testGetMemoryFilePath() {
        Path path = memoryManager.getMemoryFilePath();
        assertNotNull(path);
        assertTrue(path.toString().contains(testSessionId));
        assertTrue(path.toString().contains("session-memory.md"));
    }

    @Test
    void testDefaultMemoryTemplateStructure() {
        String template = SessionMemoryManager.getDefaultMemoryTemplate();

        assertTrue(template.contains("# Session Memory"));
        assertTrue(template.contains("## 关键决策"));
        assertTrue(template.contains("## 错误与修复"));
        assertTrue(template.contains("## 当前进度"));
    }
}
