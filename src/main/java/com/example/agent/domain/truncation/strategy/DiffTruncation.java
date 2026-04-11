package com.example.agent.domain.truncation.strategy;

import com.example.agent.domain.truncation.TruncationStrategy;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DiffTruncation implements TruncationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DiffTruncation.class);
    private static final int MAX_FILES_IN_DIFF = 15;
    private static final int MAX_CONTEXT_LINES_PER_HUNK = 3;

    private final TokenEstimator tokenEstimator;

    public DiffTruncation(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public String truncate(String content, int maxTokens) {
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= maxTokens) {
            return content;
        }

        logger.debug("Git Diff 截断: {} tokens > {}", originalTokens, maxTokens);

        String[] lines = content.split("\n");
        List<String> result = new ArrayList<>();
        int fileCount = 0;
        boolean inFile = false;
        int contextCount = 0;

        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                fileCount++;
                if (fileCount > MAX_FILES_IN_DIFF) {
                    continue;
                }
                inFile = true;
                contextCount = 0;
            }

            if (fileCount > MAX_FILES_IN_DIFF) {
                continue;
            }

            if (isDiffHeader(line)) {
                result.add(line);
                contextCount = 0;
            } else if (isCodeChange(line)) {
                result.add(line);
                contextCount = 0;
            } else if (isContextLine(line) && contextCount < MAX_CONTEXT_LINES_PER_HUNK) {
                result.add(line);
                contextCount++;
            } else if (inFile && !isContextLine(line)) {
                result.add(line);
            }
        }

        if (fileCount > MAX_FILES_IN_DIFF) {
            result.add("");
            result.add(String.format("... [省略了 %d 个文件的改动，请查看完整文件] ...",
                    fileCount - MAX_FILES_IN_DIFF));
        }

        String truncated = String.join("\n", result);
        int resultTokens = tokenEstimator.estimateTextTokens(truncated);

        return addTruncationNotice(truncated, originalTokens, resultTokens);
    }

    @Override
    public boolean supports(String contentType) {
        return true;
    }

    private boolean isDiffHeader(String line) {
        return line.startsWith("diff --git") ||
               line.startsWith("index ") ||
               line.startsWith("--- ") ||
               line.startsWith("+++ ") ||
               line.startsWith("@@ ");
    }

    private boolean isCodeChange(String line) {
        return (line.startsWith("+") && line.length() > 1 && !line.startsWith("+++")) ||
               (line.startsWith("-") && line.length() > 1 && !line.startsWith("---"));
    }

    private boolean isContextLine(String line) {
        return line.startsWith(" ") && !line.startsWith("  ");
    }

    private String addTruncationNotice(String content, int originalTokens, int remainingTokens) {
        String notice = String.format("\n// ... [Diff 截断，原 %d tokens，保留 %d tokens] ...\n",
                originalTokens, remainingTokens);
        return content + notice;
    }
}
