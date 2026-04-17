package com.example.agent.core;

import java.util.Set;

public enum AgentMode {

    CHAT("💬", "聊天模式", "只读探索，提供建议，不修改文件",
        Set.of("glob", "grep", "list_directory", "read_file", "search_code", "ask_user")
    ),

    CODING("🛠️", "编程模式", "全权限执行，自动完成任务",
        Set.of("glob", "grep", "list_directory", "read_file", "search_code", 
               "write_file", "edit_file", "bash", "ask_user", "todo_write")
    );

    private final String icon;
    private final String displayName;
    private final String description;
    private final Set<String> allowedTools;

    AgentMode(String icon, String displayName, String description, Set<String> allowedTools) {
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
        this.allowedTools = allowedTools;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public boolean isToolAllowed(String toolName) {
        return allowedTools.contains(toolName);
    }

    public String getFullDisplayName() {
        return icon + " " + displayName;
    }
}
