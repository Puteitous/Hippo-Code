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
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        this.tokenEstimator = tokenEstimator;
        if (config != null) {
            this.maxTokens = config.getMaxTokens();
            this.strategy = config.getTruncateStrategy();
        } else {
            this.maxTokens = ContextConfig.DEFAULT_TOOL_RESULT_MAX_TOKENS;
            this.strategy = ContextConfig.ToolResultConfig.DEFAULT_TRUNCATE_STRATEGY;
        }
    }

    public TruncateCompressor(TokenEstimator tokenEstimator, int maxTokens, String strategy) {
        if (tokenEstimator == null) {
            throw new IllegalArgumentException("tokenEstimator不能为null");
        }
        this.tokenEstimator = tokenEstimator;
        this.maxTokens = maxTokens;
        this.strategy = strategy != null ? strategy : ContextConfig.ToolResultConfig.DEFAULT_TRUNCATE_STRATEGY;
    }

    @Override
    public Message compress(Message message, int maxTokens) {
        if (message == null) {
            return null;
        }
        
        String content = message.getContent();
        if (content == null || content.isEmpty()) {
            return createCopy(message, content);
        }

        if (maxTokens <= 0) {
            return createCopy(message, content);
        }

        int currentTokens = tokenEstimator.estimateTextTokens(content);
        
        String finalContent;
        if (currentTokens <= maxTokens) {
            finalContent = content;
        } else {
            finalContent = truncateContent(content, maxTokens);
        }
        
        return createCopy(message, finalContent);
    }
    
    private Message createCopy(Message original, String content) {
        Message copy = new Message();
        copy.setRole(original.getRole());
        copy.setContent(content);
        copy.setToolCallId(original.getToolCallId());
        copy.setName(original.getName());
        copy.setToolCalls(original.getToolCalls());
        return copy;
    }

    @Override
    public boolean supports(Message message) {
        if (message == null) {
            return false;
        }
        return message.getRole() != null && "tool".equals(message.getRole());
    }

    private String truncateContent(String content, int targetTokens) {
        int targetChars = targetTokens * 4;
        
        if (content.length() <= targetChars) {
            return content;
        }

        int minContentChars = 50;
        String suffix = "\n\n... [已截断，原长度: " + content.length() + " 字符]";
        
        if (targetChars < suffix.length() + minContentChars) {
            return content.substring(0, Math.max(0, targetChars));
        }

        String truncated;

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
