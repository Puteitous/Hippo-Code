package com.example.agent.memory.model;

public enum MemoryTag {

    SYSTEM_RULE("系统规则"),
    USER_REQUIREMENT("用户需求"),
    ERROR_INFO("错误信息"),
    CODE_CONTENT("代码内容"),
    TOOL_RESULT("工具结果"),
    LARGE_CONTENT("大型内容"),
    DEBUG_LOG("调试日志"),
    IMPORTANT_DECISION("重要决策"),
    INTERMEDIATE_RESULT("中间结果");

    private final String displayName;

    MemoryTag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
