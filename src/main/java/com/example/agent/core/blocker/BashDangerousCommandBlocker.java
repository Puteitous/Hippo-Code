package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

public class BashDangerousCommandBlocker implements Blocker {

    private static final Set<String> DANGEROUS_PATTERNS = Set.of(
        "rm -rf", "rm -fr", "rmdir /s", "del /f", "del /s",
        "format", "fdisk", "mkfs", "dd if=",
        "sudo", "su root", "chmod 777", "chown",
        "> /dev/", "shutdown", "reboot", "halt",
        ":(){ :|:& };:", "fork bomb"
    );

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if (!"bash".equals(toolName)) {
            return HookResult.allow();
        }

        if (!arguments.has("command")) {
            return HookResult.allow();
        }

        String command = arguments.get("command").asText().toLowerCase();

        for (String pattern : DANGEROUS_PATTERNS) {
            if (command.contains(pattern)) {
                return HookResult.deny(
                    String.format("检测到危险命令模式: %s", pattern),
                    "为了系统安全，该操作已被禁止。如需执行请使用更安全的方式"
                );
            }
        }

        return HookResult.allow();
    }
}
