package com.example.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 记忆工具沙箱
 * 
 * 严格限制记忆提取过程只能读写 .hippo/memory/ 目录，无权触碰项目源代码
 */
public class MemoryToolSandbox {

    private static final Logger logger = LoggerFactory.getLogger(MemoryToolSandbox.class);
    
    private final Path memoryRoot;

    public MemoryToolSandbox(Path memoryRoot) {
        this.memoryRoot = memoryRoot;
    }

    /**
     * 检查工具调用是否被允许
     * 
     * @param toolName 工具名称
     * @param input 工具输入参数
     * @return 权限检查结果
     */
    public MemoryPermissionResult check(String toolName, Map<String, Object> input) {
        if (input == null) {
            input = Map.of();
        }

        ToolCategory category = ToolCategory.fromToolName(toolName);

        return switch (category) {
            case SAFE_READ -> checkReadOnlyTool(input);
            case SAFE_WRITE -> checkWriteFile(input);
            case BASH -> checkBash(input);
            case DANGEROUS -> MemoryPermissionResult.deny("不支持的工具：" + toolName);
        };
    }

    /**
     * 检查读文件操作
     */
    private MemoryPermissionResult checkReadFile(Map<String, Object> input) {
        String filePath = extractPath(input);
        if (filePath == null || filePath.isEmpty()) {
            return MemoryPermissionResult.deny("文件路径为空");
        }

        // 读文件允许访问任何位置（记忆系统需要读取项目文件来分析）
        return MemoryPermissionResult.allow();
    }

    /**
     * 检查写文件操作（write_file, edit_file）
     */
    private MemoryPermissionResult checkWriteFile(Map<String, Object> input) {
        String filePath = extractPath(input);
        if (filePath == null || filePath.isEmpty()) {
            return MemoryPermissionResult.deny("文件路径为空");
        }

        if (!isWithinMemoryDir(filePath)) {
            return MemoryPermissionResult.deny(
                "只能写入 memory 目录：" + memoryRoot + "，尝试写入：" + filePath
            );
        }

        return MemoryPermissionResult.allow();
    }

    /**
     * 检查只读工具（glob, grep）
     */
    private MemoryPermissionResult checkReadOnlyTool(Map<String, Object> input) {
        // 这些工具本身就是只读的，允许使用
        return MemoryPermissionResult.allow();
    }

    /**
     * 检查 Bash 命令
     */
    private MemoryPermissionResult checkBash(Map<String, Object> input) {
        String command = extractCommand(input);
        if (command == null || command.trim().isEmpty()) {
            return MemoryPermissionResult.deny("Bash 命令为空");
        }

        BashTool.SafetyLevel level = BashTool.assessSafetyLevel(command);
        
        // 只允许 SAFE 和 LOW_RISK（管道）操作
        if (level != BashTool.SafetyLevel.SAFE && level != BashTool.SafetyLevel.LOW_RISK) {
            return MemoryPermissionResult.deny(
                "Bash 仅限只读命令，检测到" + level + "操作：" + command
            );
        }

        return MemoryPermissionResult.allow();
    }

    /**
     * 检查路径是否在 memory 目录内
     * 
     * 使用规范化路径 + URL 解码防止目录遍历攻击
     */
    private boolean isWithinMemoryDir(String targetPath) {
        try {
            // 先解码 URL 编码的字符（如 %2e → .）
            String decodedPath = URLDecoder.decode(targetPath, StandardCharsets.UTF_8);
            
            Path normalizedTarget = Paths.get(decodedPath).toAbsolutePath().normalize();
            Path normalizedRoot = memoryRoot.toAbsolutePath().normalize();
            
            // 检查规范化后的路径是否以 memoryRoot 开头
            return normalizedTarget.startsWith(normalizedRoot);
        } catch (Exception e) {
            logger.debug("路径检查失败：{} (memoryRoot: {})", targetPath, memoryRoot, e);
            return false;
        }
    }

    /**
     * 从输入参数中提取文件路径
     */
    private String extractPath(Map<String, Object> input) {
        Object path = input.get("file_path");
        if (path == null) {
            path = input.get("path");
        }
        return path != null ? path.toString() : null;
    }

    /**
     * 从输入参数中提取 Bash 命令
     */
    private String extractCommand(Map<String, Object> input) {
        Object command = input.get("command");
        return command != null ? command.toString() : null;
    }

    /**
     * 获取 memory 根目录
     */
    public Path getMemoryRoot() {
        return memoryRoot;
    }
}
