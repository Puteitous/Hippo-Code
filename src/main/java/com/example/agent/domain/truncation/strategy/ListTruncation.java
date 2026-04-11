package com.example.agent.domain.truncation.strategy;

import com.example.agent.domain.truncation.TruncationStrategy;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListTruncation implements TruncationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ListTruncation.class);
    private static final int DEFAULT_MAX_ITEMS = 20;
    private static final int MAX_DETAIL_LINES_PER_ITEM = 10;

    private final TokenEstimator tokenEstimator;

    public ListTruncation(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public String truncate(String content, int maxTokens) {
        int originalTokens = tokenEstimator.estimateTextTokens(content);
        if (originalTokens <= maxTokens) {
            return content;
        }

        logger.debug("列表/搜索结果截断: {} tokens > {}", originalTokens, maxTokens);

        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();

        int itemCount = 0;
        int detailLines = 0;
        boolean inItem = false;

        for (String line : lines) {
            if (isItemSeparator(line)) {
                itemCount++;
                detailLines = 0;
                inItem = true;

                if (itemCount > DEFAULT_MAX_ITEMS) {
                    break;
                }
            } else if (inItem) {
                detailLines++;
                if (detailLines > MAX_DETAIL_LINES_PER_ITEM) {
                    continue;
                }
            }

            result.append(line).append("\n");
        }

        if (itemCount > DEFAULT_MAX_ITEMS) {
            result.append("\n... [只显示前 ").append(DEFAULT_MAX_ITEMS)
                  .append(" 个结果，剩余省略] ...\n");
        }

        String truncated = result.toString();
        int resultTokens = tokenEstimator.estimateTextTokens(truncated);

        return addTruncationNotice(truncated, originalTokens, resultTokens);
    }

    @Override
    public boolean supports(String contentType) {
        return true;
    }

    private boolean isItemSeparator(String line) {
        String trimmed = line.trim();
        return trimmed.matches("^\\[?\\d+\\]?\\s*.*") ||
               trimmed.matches("^\\d+\\.\\s*.*") ||
               trimmed.startsWith("📄") ||
               trimmed.startsWith("[1]") ||
               (trimmed.length() > 0 && Character.isDigit(trimmed.charAt(0)));
    }

    private String addTruncationNotice(String content, int originalTokens, int remainingTokens) {
        String notice = String.format("\n// ... [列表截断，原 %d tokens，保留 %d tokens] ...\n",
                originalTokens, remainingTokens);
        return content + notice;
    }
}
