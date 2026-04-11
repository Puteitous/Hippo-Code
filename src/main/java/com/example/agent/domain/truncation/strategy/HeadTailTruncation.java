package com.example.agent.domain.truncation.strategy;

import com.example.agent.domain.truncation.TruncationStrategy;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeadTailTruncation implements TruncationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HeadTailTruncation.class);
    private static final float HEAD_RATIO = 0.6f;
    private static final float TAIL_RATIO = 0.4f;

    private final TokenEstimator tokenEstimator;

    public HeadTailTruncation(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public String truncate(String content, int maxTokens) {
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= maxTokens) {
            return content;
        }

        logger.debug("通用头尾截断: {} tokens > {}", originalTokens, maxTokens);

        String[] lines = content.split("\n");

        int headLines = (int) (lines.length * HEAD_RATIO);
        int tailLines = (int) (lines.length * TAIL_RATIO);

        while (headLines + tailLines > lines.length) {
            if (headLines > tailLines) {
                headLines--;
            } else {
                tailLines--;
            }
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < headLines; i++) {
            result.append(lines[i]).append("\n");
        }

        result.append("\n... [中间截断] ...\n\n");

        for (int i = lines.length - tailLines; i < lines.length; i++) {
            result.append(lines[i]).append("\n");
        }

        String truncated = result.toString();
        int resultTokens = tokenEstimator.estimateTextTokens(truncated);

        while (resultTokens > maxTokens && headLines > 3) {
            headLines--;
            result = new StringBuilder();
            for (int i = 0; i < headLines; i++) {
                result.append(lines[i]).append("\n");
            }
            result.append("\n... [中间截断] ...\n\n");
            for (int i = lines.length - tailLines; i < lines.length; i++) {
                result.append(lines[i]).append("\n");
            }
            truncated = result.toString();
            resultTokens = tokenEstimator.estimateTextTokens(truncated);
        }

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
