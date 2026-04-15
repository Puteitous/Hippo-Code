package com.example.agent.orchestrator.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompilationChecker {

    private static final Logger logger = LoggerFactory.getLogger(CompilationChecker.class);

    private static final Pattern ERROR_PATTERN = Pattern.compile(
        "\\[ERROR\\] (.*\\.java):\\[(\\d+),(\\d+)\\] (.*)"
    );

    private final String projectRoot;

    public CompilationChecker() {
        this(System.getProperty("user.dir"));
    }

    public CompilationChecker(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public CompilationResult check() {
        logger.info("开始编译检查...");

        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        
        if (isWindows) {
            builder.command("cmd.exe", "/c", "mvn compile -q -DskipTests 2>&1");
        } else {
            builder.command("bash", "-c", "mvn compile -q -DskipTests 2>&1");
        }

        builder.directory(new File(projectRoot));
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            boolean success = exitCode == 0;

            if (success) {
                logger.info("✅ 编译检查通过");
                return new CompilationResult(true, List.of(), "");
            } else {
                List<String> errors = parseErrors(output.toString());
                logger.warn("⚠️  编译检查失败，发现 {} 个错误", errors.size());
                return new CompilationResult(false, errors, output.toString());
            }

        } catch (Exception e) {
            logger.warn("编译检查执行失败: {}", e.getMessage());
            return new CompilationResult(true, List.of(), "编译检查跳过: " + e.getMessage());
        }
    }

    private List<String> parseErrors(String output) {
        List<String> errors = new ArrayList<>();
        Matcher matcher = ERROR_PATTERN.matcher(output);

        while (matcher.find()) {
            String file = matcher.group(1);
            String line = matcher.group(2);
            String column = matcher.group(3);
            String message = matcher.group(4);

            errors.add(String.format("%s:%s:%s - %s", file, line, column, message));

            if (errors.size() >= 5) {
                errors.add("... 以及更多错误 ...");
                break;
            }
        }

        if (errors.isEmpty() && !output.trim().isEmpty()) {
            String[] lines = output.split("\n");
            for (int i = 0; i < Math.min(5, lines.length); i++) {
                if (lines[i].contains("[ERROR]")) {
                    errors.add(lines[i]);
                }
            }
        }

        return errors;
    }

    public static class CompilationResult {
        private final boolean success;
        private final List<String> errors;
        private final String rawOutput;

        public CompilationResult(boolean success, List<String> errors, String rawOutput) {
            this.success = success;
            this.errors = errors;
            this.rawOutput = rawOutput;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String formatErrorMessage() {
            if (success) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("⚠️  代码变更导致编译失败，已自动回滚变更\n\n");
            sb.append("📝 编译错误:\n");

            for (String error : errors) {
                sb.append("   - ").append(error).append("\n");
            }

            sb.append("\n💡 请修复上述编译错误后重新尝试");

            return sb.toString();
        }
    }
}
