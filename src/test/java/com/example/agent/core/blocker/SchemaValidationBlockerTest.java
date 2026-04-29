package com.example.agent.core.blocker;

import com.example.agent.tools.BashTool;
import com.example.agent.tools.EditFileTool;
import com.example.agent.tools.ReadFileTool;
import com.example.agent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidationBlockerTest {

    private SchemaValidationBlocker blocker;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new EditFileTool());
        toolRegistry.register(new BashTool());
        blocker = new SchemaValidationBlocker(toolRegistry);
    }

    @Test
    void editFileWithoutPath_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("old_text", "old")
                .put("new_text", "new");

        HookResult result = blocker.check("edit_file", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("缺少必需参数"));
        assertTrue(result.getReason().contains("path"));
        assertTrue(result.getSuggestion().contains("正确示例"));
    }

    @Test
    void editFileWithoutOldText_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java")
                .put("new_text", "new");

        HookResult result = blocker.check("edit_file", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("old_text"));
    }

    @Test
    void editFileWithAllParams_shouldBeAllowed() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("path", "/test/Test.java")
                .put("old_text", "old content")
                .put("new_text", "new content");

        HookResult result = blocker.check("edit_file", args);

        assertTrue(result.isAllowed());
    }

    @Test
    void readFileWithoutPath_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode();

        HookResult result = blocker.check("read_file", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("path"));
    }

    @Test
    void bashWithoutCommand_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode();

        HookResult result = blocker.check("bash", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("command"));
    }

    @Test
    void getRequiredFields_shouldReturnCorrectFields() {
        assertTrue(blocker.getRequiredFields("edit_file").contains("path"));
        assertTrue(blocker.getRequiredFields("edit_file").contains("old_text"));
        assertTrue(blocker.getRequiredFields("edit_file").contains("new_text"));
        assertTrue(blocker.getRequiredFields("read_file").contains("path"));
    }

    @Test
    void multipleMissingFields_shouldListAllMissing() {
        JsonNode args = JsonNodeFactory.instance.objectNode();

        HookResult result = blocker.check("edit_file", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("path"));
        assertTrue(result.getReason().contains("old_text"));
        assertTrue(result.getReason().contains("new_text"));
    }
}
