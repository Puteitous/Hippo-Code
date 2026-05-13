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
        "ls", "dir", "cat", "type", "more", "pwd", "echo", "mkdir", "touch",
        "grep", "find", "findstr", "wc", "head", "tail", "sort", "uniq",
        "curl", "wget", "where"
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

        if (command.contains(";") || command.contains("&&") ||
            command.contains("||") || command.contains("`") ||
            command.contains("$(")) {
            throw new ToolExecutionException(
                "安全限制: 检测到危险的 shell 操作符。\n" +
                "禁止使用命令链接（;、&&、||）和命令替换（`、$()）。"
            );
        }

        for (String pattern : DANGEROUS_PATTERNS) {
            if (command.contains(pattern)) {
                throw new ToolExecutionException("安全限制: 检测到危险命令模式 '" + pattern + "'。\n为了系统安全，此类命令被禁止执行。");
            }
        }

        String commandName = extractCommandName(command);

        if (BLOCKED_COMMANDS.contains(commandName)) {
            throw new ToolExecutionException("安全限制: 命令 '" + commandName + "' 被禁止执行。\n为了系统安全，此类操作被禁止。");
        }

        if (!ALLOWED_COMMANDS.contains(commandName)) {
            throw new ToolExecutionException("安全限制: 命令 '" + commandName + "' 不在允许列表中。\n允许的命令: " + String.join(", ", ALLOWED_COMMANDS));
        }

        if (arguments.has("timeout")) {
            int timeout = arguments.get("timeout").asInt();
            if (timeout < 1 || timeout > 300) {
                throw new ToolExecutionException("超时时间必须在 1-300 秒之间");
            }
        }
    }

    private String extractCommandName(String command) {
        String firstPart = command.split("\\|")[0].trim();
        firstPart = firstPart.split(">")[0].trim();
        firstPart = firstPart.split(">>")[0].trim();
        String[] parts = firstPart.split("\\s+");
        if (parts.length > 0) {
            String cmd = parts[0];
            int lastSlash = cmd.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < cmd.length() - 1) {
                return cmd.substring(lastSlash + 1).toLowerCase();
            }
            return cmd.toLowerCase();
        }
        return command.toLowerCase();
    }
}
