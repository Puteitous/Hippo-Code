package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class BashTool implements ToolExecutor {

    private static final int DEFAULT_TIMEOUT = 30;
    private static final int MAX_TIMEOUT = 300;
    
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
        "git", "mvn", "gradle", "npm", "yarn", "pnpm",
        "javac", "java", "jar", "javadoc",
        "ls", "dir", "cat", "pwd", "echo", "mkdir", "touch",
        "grep", "find", "wc", "head", "tail", "sort", "uniq",
        "curl", "wget"
    ));
    
    private static final Set<String> BLOCKED_COMMANDS = new HashSet<>(Arrays.asList(
        "rm", "del", "rmdir", "rd", "format", "fdisk",
        "sudo", "su", "chmod", "chown",
        "shutdown", "reboot", "halt", "poweroff",
        "dd", "mkfs", "fsck"
    ));
    
    private static final Set<String> DANGEROUS_PATTERNS = new HashSet<>(Arrays.asList(
        "rm -rf", "del /s", "format", "fdisk",
        "sudo", "chmod 777", "chown",
        "> /dev/", "dd if=", ":(){ :|:& };:"
    ));

    @Override
    public String getName() {
        return "bash";
    }

    @Override
    public String getDescription() {
        return "执行终端命令。支持构建工具（mvn, gradle, npm）、版本控制、文件操作等。" +
               "出于安全考虑，只允许执行白名单内的命令，禁止危险操作。" +
               "执行前会进行安全检查，危险命令需要用户确认。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "command": {
                        "type": "string",
                        "description": "要执行的命令（注意：只允许白名单内的命令）"
                    },
                    "timeout": {
                        "type": "integer",
                        "description": "超时时间（秒，默认 30，最大 300）",
                        "default": 30,
                        "minimum": 1,
                        "maximum": 300
                    },
                    "working_dir": {
                        "type": "string",
                        "description": "工作目录（默认为项目根目录）"
                    }
                },
                "required": ["command"]
            }
            """;
    }

    @Override
    public List<String> getAffectedPaths(JsonNode arguments) {
        if (arguments.has("working_dir")) {
            return Collections.singletonList(arguments.get("working_dir").asText());
        }
        return Collections.singletonList(".");
    }

    @Override
    public boolean requiresFileLock() {
        return false;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("command") || arguments.get("command").isNull()) {
            throw new ToolExecutionException("缺少必需参数: command");
        }

        String command = arguments.get("command").asText();
        if (command == null || command.trim().isEmpty()) {
            throw new ToolExecutionException("command 参数不能为空");
        }
        command = command.trim();
        
        int timeout = DEFAULT_TIMEOUT;
        if (arguments.has("timeout") && !arguments.get("timeout").isNull()) {
            timeout = arguments.get("timeout").asInt();
        }
        
        String workingDir = ".";
        if (arguments.has("working_dir") && !arguments.get("working_dir").isNull()) {
            String dirValue = arguments.get("working_dir").asText();
            if (dirValue != null && !dirValue.trim().isEmpty()) {
                workingDir = dirValue;
            }
        }
        
        timeout = Math.max(1, Math.min(MAX_TIMEOUT, timeout));

        validateCommand(command);

        Path workPath = PathSecurityUtils.validateAndResolve(workingDir);
        
        if (!Files.exists(workPath)) {
            throw new ToolExecutionException("工作目录不存在: " + workingDir);
        }
        
        if (!Files.isDirectory(workPath)) {
            throw new ToolExecutionException("工作目录不是目录: " + workingDir);
        }

        try {
            return executeCommand(command, workPath, timeout);
        } catch (IOException e) {
            throw new ToolExecutionException("命令执行失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("命令执行被中断: " + e.getMessage(), e);
        }
    }

    private void validateCommand(String command) throws ToolExecutionException {
        String lowerCommand = command.toLowerCase();
        
        if (command.contains(">") || command.contains("|") || 
            command.contains(";") || command.contains("&&") || 
            command.contains("||") || command.contains("`") || 
            command.contains("$(")) {
            throw new ToolExecutionException(
                "安全限制: 检测到危险的 shell 操作符。\n" +
                "为了系统安全，重定向、管道、多命令等操作被禁止。"
            );
        }
        
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lowerCommand.contains(pattern.toLowerCase())) {
                throw new ToolExecutionException(
                    "安全限制: 检测到危险命令模式 '" + pattern + "'。\n" +
                    "为了系统安全，此类命令被禁止执行。"
                );
            }
        }
        
        for (String blocked : BLOCKED_COMMANDS) {
            if (lowerCommand.contains(blocked.toLowerCase())) {
                throw new ToolExecutionException(
                    "安全限制: 命令包含被禁止的操作 '" + blocked + "'。\n" +
                    "为了系统安全，此类操作被禁止。"
                );
            }
        }
        
        String baseCommand = extractBaseCommand(command);
        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            throw new ToolExecutionException(
                "安全限制: 命令 '" + baseCommand + "' 不在允许列表中。\n" +
                "允许的命令: " + String.join(", ", ALLOWED_COMMANDS) + "\n" +
                "如需执行其他命令，请联系管理员添加到白名单。"
            );
        }
    }

    private String extractBaseCommand(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return "";
        }
        return parts[0].toLowerCase();
    }

    private String executeCommand(String command, Path workPath, int timeout) 
            throws IOException, InterruptedException, ToolExecutionException {
        
        ProcessBuilder processBuilder;
        String shell;
        
        if (isWindows()) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("bash", "-c", command);
        }
        
        processBuilder.directory(workPath.toFile());
        processBuilder.redirectErrorStream(true);
        
        Map<String, String> env = processBuilder.environment();
        env.put("LANG", "en_US.UTF-8");
        
        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        if (!finished) {
            process.destroyForcibly();
            throw new ToolExecutionException(
                "命令执行超时（超过 " + timeout + " 秒）。\n" +
                "已执行的输出:\n" + output.toString()
            );
        }
        
        int exitCode = process.exitValue();
        
        return formatResult(command, output.toString(), exitCode, duration, workPath);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private String formatResult(String command, String output, int exitCode, 
                               long duration, Path workPath) {
        StringBuilder result = new StringBuilder();
        
        result.append("命令执行结果\n");
        result.append("─────────────────────────────────────────────────────────────\n");
        result.append("命令: ").append(command).append("\n");
        result.append("工作目录: ").append(PathSecurityUtils.getRelativePath(workPath)).append("\n");
        result.append("退出码: ").append(exitCode).append(" ");
        result.append(exitCode == 0 ? "✅ 成功" : "❌ 失败").append("\n");
        result.append("执行时间: ").append(duration).append(" ms\n");
        result.append("─────────────────────────────────────────────────────────────\n");
        
        if (output.isEmpty()) {
            result.append("(无输出)\n");
        } else {
            result.append("输出:\n");
            result.append(output);
            if (!output.endsWith("\n")) {
                result.append("\n");
            }
        }
        
        result.append("─────────────────────────────────────────────────────────────\n");
        
        return result.toString();
    }
}
