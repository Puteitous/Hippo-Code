package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BashDangerousCommandBlockerTest {

    private final BashDangerousCommandBlocker blocker = new BashDangerousCommandBlocker();

    @Test
    void dangerousCommand_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("command", "rm -rf /");

        HookResult result = blocker.check("bash", args);

        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("危险命令模式"));
        assertTrue(result.getSuggestion().contains("安全"));
    }

    @Test
    void formatCommand_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("command", "format C:");

        HookResult result = blocker.check("bash", args);

        assertFalse(result.isAllowed());
    }

    @Test
    void sudoCommand_shouldBeBlocked() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("command", "sudo rm -rf /tmp");

        HookResult result = blocker.check("bash", args);

        assertFalse(result.isAllowed());
    }

    @Test
    void safeCommand_shouldBeAllowed() {
        JsonNode gitArgs = JsonNodeFactory.instance.objectNode()
                .put("command", "git status");
        JsonNode mvnArgs = JsonNodeFactory.instance.objectNode()
                .put("command", "mvn compile");
        JsonNode lsArgs = JsonNodeFactory.instance.objectNode()
                .put("command", "ls -la");

        assertTrue(blocker.check("bash", gitArgs).isAllowed());
        assertTrue(blocker.check("bash", mvnArgs).isAllowed());
        assertTrue(blocker.check("bash", lsArgs).isAllowed());
    }

    @Test
    void nonBashTools_shouldBeAllowed() {
        JsonNode args = JsonNodeFactory.instance.objectNode()
                .put("command", "rm -rf /");

        assertTrue(blocker.check("edit_file", args).isAllowed());
    }
}
