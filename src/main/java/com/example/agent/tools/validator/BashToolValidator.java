package com.example.agent.tools.validator;

import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * BashTool 的参数验证器
 * 实现命令白名单和危险模式检查
 */
public class BashToolValidator implements ToolParamValidator {
    
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "git", "mvn", "gradle", "npm", "yarn", "pnpm",
        "javac", "java", "jar", "javadoc",
        "ls", "dir", "cat", "pwd", "echo", "mkdir", "touch",
        "grep", "find", "wc", "head", "tail", "sort", "uniq",
        "curl", "wget"
    );
    
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "rm", "del", "rmdir", "rd", "format", "fdisk",
        "sudo", "su", "chmod", "chown",
        "shutdown", "reboot", "halt", "poweroff",
        "dd", "mkfs", "fsck"
    );
    
    private static final Set<String> DANGEROUS_PATTERNS = Set.of(
        "rm -rf", "del /s", "format", "fdisk",
        "sudo", "chmod 777", "chown",
        "> /dev/", "dd if=", ":(){ :|:& };:"
    );
    
    @Override
    public void validateParameters(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("command") || arguments.get("command").isNull()) {
            throw new ToolExecutionException("缺少必需参数: command");
        }
        
        String command = arguments.get("command").asText();
        if (command == null || command.trim().isEmpty()) {
            throw new ToolExecutionException("command 参数不能为空");
        }
        
        // 提取命令名称
        String commandName = extractCommandName(command);
        
        // 检查是否在黑名单中
        if (BLOCKED_COMMANDS.contains(commandName)) {
            throw new ToolExecutionException("命令被禁止: " + commandName);
        }
        
        // 检查危险模式
        for (String pattern : DANGEROUS_PATTERNS) {
            if (command.contains(pattern)) {
                throw new ToolExecutionException("命令包含危险模式: " + pattern);
            }
        }
        
        // 检查是否在白名单中
        if (!ALLOWED_COMMANDS.contains(commandName)) {
            throw new ToolExecutionException("命令不在白名单中: " + commandName + 
                "。允许的命令: " + String.join(", ", ALLOWED_COMMANDS));
        }
        
        // 验证超时参数
        if (arguments.has("timeout")) {
            int timeout = arguments.get("timeout").asInt();
            if (timeout < 1 || timeout > 300) {
                throw new ToolExecutionException("超时时间必须在 1-300 秒之间");
            }
        }
    }
    
    /**
     * 从命令字符串中提取命令名称
     */
    private String extractCommandName(String command) {
        // 处理管道符
        String firstPart = command.split("\\|")[0].trim();
        // 处理重定向
        firstPart = firstPart.split(">")[0].trim();
        firstPart = firstPart.split(">>")[0].trim();
        // 获取第一个单词
        String[] parts = firstPart.split("\\s+");
        if (parts.length > 0) {
            // 处理路径前缀（如 /usr/bin/git -> git）
            String cmd = parts[0];
            int lastSlash = cmd.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < cmd.length() - 1) {
                return cmd.substring(lastSlash + 1);
            }
            return cmd;
        }
        return command;
    }
}
