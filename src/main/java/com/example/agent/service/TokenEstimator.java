package com.example.agent.service;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;

public class TokenEstimator {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    public int estimateConversationTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        int total = 0;
        for (Message msg : messages) {
            if (msg != null) {
                total += estimateMessageTokens(msg);
            }
        }
        return total;
    }

    public int estimateMessageTokens(Message msg) {
        if (msg == null) {
            return 0;
        }
        
        int tokens = 4;
        
        if (msg.getContent() != null) {
            tokens += estimateTextTokens(msg.getContent());
        }
        
        if (msg.getToolCalls() != null) {
            for (ToolCall tc : msg.getToolCalls()) {
                if (tc != null && tc.getFunction() != null && tc.getFunction().getArguments() != null) {
                    tokens += estimateTextTokens(tc.getFunction().getArguments());
                }
            }
        }
        
        return tokens;
    }

    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (CHINESE_PATTERN.matcher(Character.toString(c)).matches()) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        
        return chineseChars + otherChars / 4;
    }
}