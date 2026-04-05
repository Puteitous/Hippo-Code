package com.example.agent.intent;

public enum IntentType {
    
    CODE_GENERATION("代码生成", "用户请求生成新代码"),
    
    CODE_MODIFICATION("代码修改", "用户请求修改现有代码"),
    
    CODE_REVIEW("代码审查", "用户请求审查或分析代码"),
    
    DEBUGGING("调试问题", "用户遇到错误需要帮助调试"),
    
    FILE_OPERATION("文件操作", "用户请求文件读写操作"),
    
    PROJECT_ANALYSIS("项目分析", "用户请求分析项目结构或代码库"),
    
    QUESTION("一般问题", "用户提出一般性问题"),
    
    CLARIFICATION("澄清确认", "用户需要澄清或确认某些信息"),
    
    UNKNOWN("未知意图", "无法识别的意图");

    private final String displayName;
    private final String description;

    IntentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean requiresToolCall() {
        return this == CODE_GENERATION 
            || this == CODE_MODIFICATION 
            || this == FILE_OPERATION 
            || this == PROJECT_ANALYSIS
            || this == DEBUGGING;
    }

    public boolean requiresCodeAnalysis() {
        return this == CODE_REVIEW 
            || this == DEBUGGING 
            || this == PROJECT_ANALYSIS;
    }
}
