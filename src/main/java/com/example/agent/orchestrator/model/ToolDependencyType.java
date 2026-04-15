package com.example.agent.orchestrator.model;

public enum ToolDependencyType {

    READ_THEN_EDIT_SAME_FILE("读文件后编辑同文件", true),
    SEARCH_THEN_EDIT("搜索结果后编辑", false),
    BASH_OUTPUT_CONSUMED("Bash输出被后续使用", false),
    SAME_FILE_MULTIPLE_EDITS("同文件多次编辑", true),
    INDEPENDENT("无依赖", false);

    private final String description;
    private final boolean strict;

    ToolDependencyType(String description, boolean strict) {
        this.description = description;
        this.strict = strict;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStrict() {
        return strict;
    }
}
