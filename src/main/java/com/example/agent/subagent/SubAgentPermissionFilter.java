package com.example.agent.subagent;

import java.util.Set;

public class SubAgentPermissionFilter {
    private static final Set<String> ALLOWED_TOOLS = Set.of(
        "read_file",
        "glob",
        "grep",
        "search_code",
        "list_directory",
        "ask_user",
        "list_subagents"
    );

    private static final Set<String> FORBIDDEN_TOOLS = Set.of(
        "write_file",
        "edit_file",
        "bash",
        "fork_agent"
    );

    public boolean isToolAllowed(String toolName) {
        if (FORBIDDEN_TOOLS.contains(toolName)) {
            return false;
        }
        return ALLOWED_TOOLS.contains(toolName);
    }

    public Set<String> getAllowedTools() {
        return ALLOWED_TOOLS;
    }
}
