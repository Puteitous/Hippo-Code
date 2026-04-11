package com.example.agent.domain.truncation.strategy;

import com.example.agent.domain.truncation.TruncationStrategy;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class TreeTruncation implements TruncationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TreeTruncation.class);
    private static final int MAX_DEPTH = 4;

    private static final Set<String> NOISY_DIRS = new HashSet<>();

    static {
        NOISY_DIRS.add("node_modules");
        NOISY_DIRS.add("target");
        NOISY_DIRS.add("build");
        NOISY_DIRS.add("dist");
        NOISY_DIRS.add(".git");
        NOISY_DIRS.add(".idea");
        NOISY_DIRS.add(".vscode");
        NOISY_DIRS.add("__pycache__");
        NOISY_DIRS.add("venv");
        NOISY_DIRS.add(".venv");
        NOISY_DIRS.add("env");
        NOISY_DIRS.add(".env");
    }

    private final TokenEstimator tokenEstimator;

    public TreeTruncation(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public String truncate(String content, int maxTokens) {
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= maxTokens) {
            return content;
        }

        logger.debug("目录树截断: {} tokens > {}", originalTokens, maxTokens);

        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        Set<String> filteredDirs = new HashSet<>();

        for (String line : lines) {
            int depth = calculateDepth(line);

            String dirName = extractDirName(line);
            if (NOISY_DIRS.contains(dirName)) {
                if (!filteredDirs.contains(dirName)) {
                    filteredDirs.add(dirName);
                    result.append(line, 0, Math.min(line.length(), 40))
                          .append(" ... [过滤冗余目录]").append("\n");
                }
                continue;
            }

            if (depth <= MAX_DEPTH) {
                result.append(line).append("\n");
            }
        }

        if (!filteredDirs.isEmpty()) {
            result.append("\n// 已自动过滤冗余目录: ").append(filteredDirs).append("\n");
        }

        String truncated = result.toString();
        int resultTokens = tokenEstimator.estimateTextTokens(truncated);

        return addTruncationNotice(truncated, originalTokens, resultTokens);
    }

    @Override
    public boolean supports(String contentType) {
        return true;
    }

    private int calculateDepth(String line) {
        int depth = 0;
        for (char c : line.toCharArray()) {
            if (c == '│' || c == '├' || c == '└' || c == ' ' || c == ' ') {
                depth++;
            } else {
                break;
            }
        }
        return depth / 4;
    }

    private String extractDirName(String line) {
        String trimmed = line.trim()
                .replace("├", "")
                .replace("└", "")
                .replace("│", "")
                .replace("─", "")
                .replace("📁", "")
                .replace("📄", "")
                .trim();
        return trimmed.toLowerCase();
    }

    private String addTruncationNotice(String content, int originalTokens, int remainingTokens) {
        String notice = String.format("\n// ... [目录树截断，最大深度 %d，原 %d tokens，保留 %d tokens] ...\n",
                MAX_DEPTH, originalTokens, remainingTokens);
        return content + notice;
    }
}
