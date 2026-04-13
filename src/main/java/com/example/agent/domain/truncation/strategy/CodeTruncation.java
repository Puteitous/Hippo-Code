package com.example.agent.domain.truncation.strategy;

import com.example.agent.domain.truncation.SafeBreakLocator;
import com.example.agent.domain.truncation.TruncationStrategy;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeTruncation implements TruncationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CodeTruncation.class);
    private static final float HEAD_RATIO = 0.65f;
    private static final float TAIL_RATIO = 0.35f;

    private final TokenEstimator tokenEstimator;
    private final SafeBreakLocator breakLocator;

    public CodeTruncation(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
        this.breakLocator = new SafeBreakLocator();
    }

    @Override
    public String truncate(String content, int maxTokens) {
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= maxTokens || maxTokens <= 0) {
            return content;
        }

        logger.debug("通用代码截断: {} tokens > {}", originalTokens, maxTokens);

        String[] lines = content.split("\n");
        int totalLines = lines.length;

        if (totalLines == 0) {
            return content;
        }

        float tokenPerLine = (float) originalTokens / totalLines;
        int targetHeadLines = Math.round((maxTokens * HEAD_RATIO) / tokenPerLine);
        int targetTailLines = Math.round((maxTokens * TAIL_RATIO) / tokenPerLine);

        int actualHeadLines = breakLocator.findSafeBreakLine(lines, targetHeadLines).orElse(targetHeadLines);
        int actualTailStart = breakLocator.findSafeBreakLine(lines, totalLines - targetTailLines).orElse(totalLines - targetTailLines);

        actualTailStart = Math.max(actualHeadLines, actualTailStart);

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < actualHeadLines; i++) {
            result.append(lines[i]).append("\n");
        }

        if (actualTailStart > actualHeadLines) {
            result.append("\n... [中间内容已截断，前后各保留一部分] ...\n\n");
        }

        for (int i = actualTailStart; i < lines.length; i++) {
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

    private String addTruncationNotice(String content, int originalTokens, int remainingTokens) {
        String notice = String.format("\n// ... [截断，原 %d tokens，保留 %d tokens] ...\n",
                originalTokens, remainingTokens);
        return content + notice;
    }
}
