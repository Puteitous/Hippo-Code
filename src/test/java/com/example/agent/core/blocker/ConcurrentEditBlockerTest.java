package com.example.agent.core.blocker;

import com.example.agent.tools.concurrent.FileLockManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentEditBlockerTest {

    private ConcurrentEditBlocker blocker;
    private FileLockManager lockManager;

    @BeforeEach
    void setUp() {
        blocker = new ConcurrentEditBlocker();
        lockManager = FileLockManager.getInstance();
        lockManager.clear();
    }

    @Test
    void editWithoutLock_shouldBeAllowed() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java");

        HookResult result = blocker.check("edit_file", args);

        assertTrue(result.isAllowed());
    }

    @Test
    void editWithLock_shouldBeBlocked() {
        String testPath = "/test/Test.java";
        lockManager.tryAcquireLock(testPath);

        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", testPath);

        HookResult result = blocker.check("edit_file", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("正在被其他操作编辑"));

        lockManager.releaseLock(testPath);
    }

    @Test
    void writeFileWithLock_shouldBeBlocked() {
        String testPath = "/test/Test.java";
        lockManager.tryAcquireLock(testPath);

        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", testPath);

        HookResult result = blocker.check("write_file", args);

        assertFalse(result.isAllowed());

        lockManager.releaseLock(testPath);
    }

    @Test
    void nonWriteTools_shouldAlwaysBeAllowed() {
        String testPath = "/test/Test.java";
        lockManager.tryAcquireLock(testPath);

        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", testPath);

        assertTrue(blocker.check("read_file", args).isAllowed());
        assertTrue(blocker.check("glob", args).isAllowed());
        assertTrue(blocker.check("bash", args).isAllowed());

        lockManager.releaseLock(testPath);
    }
}
