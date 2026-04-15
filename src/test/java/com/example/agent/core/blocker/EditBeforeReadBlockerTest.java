package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditBeforeReadBlockerTest {

    private EditBeforeReadBlocker blocker;

    @BeforeEach
    void setUp() {
        blocker = new EditBeforeReadBlocker();
        blocker.reset();
    }

    @Test
    void editWithoutRead_shouldBeBlocked() {
        JsonNode editArgs = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java");

        HookResult result = blocker.check("edit_file", editArgs);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("未读取文件内容"));
        assertTrue(result.getSuggestion().contains("read_file"));
    }

    @Test
    void editAfterRead_shouldBeAllowed() {
        JsonNode readArgs = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java");
        JsonNode editArgs = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java");

        blocker.check("read_file", readArgs);
        HookResult result = blocker.check("edit_file", editArgs);

        assertTrue(result.isAllowed());
    }

    @Test
    void writeWithoutRead_shouldBeBlocked() {
        JsonNode writeArgs = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java");

        HookResult result = blocker.check("write_file", writeArgs);

        assertFalse(result.isAllowed());
    }

    @Test
    void nonEditTools_shouldAlwaysBeAllowed() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java");

        assertTrue(blocker.check("glob", args).isAllowed());
        assertTrue(blocker.check("grep", args).isAllowed());
        assertTrue(blocker.check("ls", args).isAllowed());
    }
}
