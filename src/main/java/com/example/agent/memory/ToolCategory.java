package com.example.agent.memory;

import java.util.Map;

/**
 * 工具类别枚举
 * 
 * 用于解耦 MemoryToolSandbox 中的工具名称硬编码
 */
public enum ToolCategory {
    SAFE_READ,      // read_file, glob, grep - 无需检查
    SAFE_WRITE,     // write_file, edit_file - 需路径检查
    BASH,           // bash - 需命令解析
    DANGEROUS       // 其他工具 - 默认拒绝
    ;

    /**
     * 工具名称到类别的映射
     */
    private static final Map<String, ToolCategory> TOOL_CATEGORY_MAP = Map.ofEntries(
        Map.entry("read_file", SAFE_READ),
        Map.entry("glob", SAFE_READ),
        Map.entry("grep", SAFE_READ),
        Map.entry("write_file", SAFE_WRITE),
        Map.entry("edit_file", SAFE_WRITE),
        Map.entry("bash", BASH)
    );

    /**
     * 根据工具名称获取类别
     */
    public static ToolCategory fromToolName(String toolName) {
        return TOOL_CATEGORY_MAP.getOrDefault(toolName, DANGEROUS);
    }

    /**
     * 注册新工具类别（用于扩展）
     */
    public static void registerTool(String toolName, ToolCategory category) {
        // 注意：如果需要运行时注册，可以使用 ConcurrentHashMap
        // 当前使用不可变 Map，适合静态配置
    }
}
