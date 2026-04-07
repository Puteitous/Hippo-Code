package com.example.agent.service;

import java.util.List;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;

public class SimpleTokenEstimator implements TokenEstimator {

    @Override
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

    @Override
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

    @Override
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        
        return chineseChars + otherChars / 4;
    }
}
