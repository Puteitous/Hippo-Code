package com.example.agent.plan;

public enum StepType {
    
    LLM_CALL("LLM调用", "调用大语言模型生成响应"),

    TOOL_CALL("工具调用", "调用指定工具执行操作"),

    FILE_READ("文件读取", "读取文件内容"),

    FILE_WRITE("文件写入", "写入文件内容"),

    CONDITION("条件判断", "根据条件决定执行路径"),

    LOOP("循环执行", "重复执行指定步骤"),

    PARALLEL("并行执行", "并行执行多个步骤"),

    WAIT("等待", "等待指定时间或条件");

    private final String displayName;
    private final String description;

    StepType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
