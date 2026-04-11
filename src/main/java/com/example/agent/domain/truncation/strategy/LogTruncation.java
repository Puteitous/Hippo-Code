package com.example.agent.domain.truncation.strategy;

import com.example.agent.domain.truncation.TruncationStrategy;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LogTruncation implements TruncationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LogTruncation.class);
    private static final float TAIL_RATIO = 0.8f;
    private static final int MIN_ERROR_LINES = 50;

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            ".*(ERROR|error|Error|Exception|exception|FAILED|failed|FAIL|fail).*");
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "^\\s+at\\s+[\\w.$<>]+\\([\\w.:]+\\)");
    private static final Pattern CausedBy_PATTERN = Pattern.compile(
            "^Caused by:.*");

    private final TokenEstimator tokenEstimator;

    public LogTruncation(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public String truncate(String content, int maxTokens) {
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= maxTokens) {
            return content;
        }

        logger.debug("日志/命令输出截断: {} tokens > {}", originalTokens, maxTokens);

        String[] lines = content.split("\n");
        int totalLines = lines.length;

        List<String> errorContext = extractErrorContext(lines);
        int errorLines = errorContext.size();

        float tokenPerLine = (float) originalTokens / totalLines;
        int targetTailLines = Math.round((maxTokens * TAIL_RATIO) / tokenPerLine);
        targetTailLines = Math.max(targetTailLines, errorLines + MIN_ERROR_LINES);

        int tailStart = Math.max(0, totalLines - targetTailLines);

        StringBuilder result = new StringBuilder();

        if (tailStart > 0) {
            result.append("// ... 日志开头省略 ").append(tailStart).append(" 行 ...\n\n");
        }

        if (!errorContext.isEmpty() && tailStart > totalLines - targetTailLines + errorLines) {
            result.append("═══════════════════════════════════════════\n");
            result.append("⚠️  检测到错误上下文:\n");
            result.append("═══════════════════════════════════════════\n");
            for (String line : errorContext) {
                result.append(line).append("\n");
            }
            result.append("\n═══════════════════════════════════════════\n");
            result.append("📝 最新输出:\n");
            result.append("═══════════════════════════════════════════\n");
        }

        for (int i = tailStart; i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }

        String truncated = result.toString();
        int resultTokens = tokenEstimator.estimateTextTokens(truncated);

        return addTruncationNotice(truncated, originalTokens, resultTokens);
    }

    @Override
    public boolean supports(String contentType) {
        return true;
    }

    private List<String> extractErrorContext(String[] lines) {
        List<String> errors = new ArrayList<>();
        boolean inStackTrace = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (ERROR_PATTERN.matcher(line).matches()) {
                addWithContext(lines, i, errors, 3, 10);
                inStackTrace = true;
            } else if (inStackTrace && (STACK_TRACE_PATTERN.matcher(line).matches()
                    || CausedBy_PATTERN.matcher(line).matches())) {
                errors.add(line);
            } else if (inStackTrace && !line.isBlank()) {
                inStackTrace = false;
            }
        }

        return errors;
    }

    private void addWithContext(String[] lines, int index, List<String> result, int before, int after) {
        int start = Math.max(0, index - before);
        int end = Math.min(lines.length, index + after + 1);
        for (int i = start; i < end; i++) {
            result.add(lines[i]);
        }
    }

    private String addTruncationNotice(String content, int originalTokens, int remainingTokens) {
        String notice = String.format("\n// ... [日志截断，原 %d tokens，保留 %d tokens] ...\n",
                originalTokens, remainingTokens);
        return content + notice;
    }
}
