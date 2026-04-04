package com.example.agent.context.compressor;

import com.example.agent.context.Compressor;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

public class TruncateCompressor implements Compressor {

    private final TokenEstimator tokenEstimator;
    private final int maxTokens;
    private final String strategy;

    public TruncateCompressor(TokenEstimator tokenEstimator, ContextConfig.ToolResultConfig config) {
        this.tokenEstimator = tokenEstimator;
        this.maxTokens = config.getMaxTokens();
        this.strategy = config.getTruncateStrategy();
    }

    public TruncateCompressor(TokenEstimator tokenEstimator, int maxTokens, String strategy) {
        this.tokenEstimator = tokenEstimator;
        this.maxTokens = maxTokens;
        this.strategy = strategy;
    }

    @Override
    public Message compress(Message message, int maxTokens) {
        if (message.getContent() == null || message.getContent().isEmpty()) {
            return message;
        }

        String content = message.getContent();
        int currentTokens = tokenEstimator.estimateTextTokens(content);
        
        if (currentTokens <= maxTokens) {
            return message;
        }

        String truncated = truncateContent(content, maxTokens);
        
        Message compressed = new Message();
        compressed.setRole(message.getRole());
        compressed.setContent(truncated);
        compressed.setToolCallId(message.getToolCallId());
        compressed.setName(message.getName());
        compressed.setToolCalls(message.getToolCalls());
        
        return compressed;
    }

    @Override
    public boolean supports(Message message) {
        return message.getRole() != null && "tool".equals(message.getRole());
    }

    private String truncateContent(String content, int targetTokens) {
        int targetChars = targetTokens * 4;
        
        if (content.length() <= targetChars) {
            return content;
        }

        String truncated;
        String suffix = "\n\n... [已截断，原长度: " + content.length() + " 字符]";

        switch (strategy.toLowerCase()) {
            case "head":
                int headChars = targetChars - suffix.length() - 20;
                truncated = content.substring(0, Math.max(0, headChars)) + suffix;
                break;
            case "smart":
                truncated = smartTruncate(content, targetChars, suffix);
                break;
            case "tail":
            default:
                int tailChars = targetChars - suffix.length() - 20;
                truncated = suffix + "\n\n" + 
                    content.substring(Math.max(0, content.length() - tailChars));
                break;
        }

        return truncated;
    }

    private String smartTruncate(String content, int targetChars, String suffix) {
        int tailRatio = 70;
        int tailChars = (int) ((targetChars - suffix.length()) * tailRatio / 100);
        int headChars = (targetChars - suffix.length()) - tailChars;

        StringBuilder sb = new StringBuilder();
        
        if (headChars > 100) {
            sb.append(content.substring(0, headChars));
            sb.append("\n\n... [中间部分已省略] ...\n\n");
        }
        
        sb.append(content.substring(Math.max(0, content.length() - tailChars)));
        sb.append(suffix);

        return sb.toString();
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public String getStrategy() {
        return strategy;
    }
}
