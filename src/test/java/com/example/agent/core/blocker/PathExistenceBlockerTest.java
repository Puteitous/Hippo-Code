package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathExistenceBlockerTest {

    private final PathExistenceBlocker blocker = new PathExistenceBlocker();

    @Test
    void editNonExistentFile_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/nonexistent/File.java");

        HookResult result = blocker.check("edit_file", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("不存在"));
        assertTrue(result.getSuggestion().contains("glob"));
    }

    @Test
    void editExistentFile_shouldBeAllowed() throws Exception {
        Path tempFile = Files.createTempFile("test", ".java");
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", tempFile.toString());

        HookResult result = blocker.check("edit_file", args);

        assertTrue(result.isAllowed());
        Files.deleteIfExists(tempFile);
    }

    @Test
    void readNonExistentFile_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/nonexistent/File.java");

        HookResult result = blocker.check("read_file", args);

        assertFalse(result.isAllowed());
    }

    @Test
    void nonPathTools_shouldBeAllowed() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/nonexistent/File.java");

        assertTrue(blocker.check("bash", args).isAllowed());
        assertTrue(blocker.check("glob", args).isAllowed());
    }
}
