// 文件路径: e:\Trae_projects\Hippo Code\src\main\java\com\example\agent\service\TokenEstimator.java
package com.example.agent.service;

import com.example.agent.llm.Message;
import com.example.agent.llm.ToolCall;

import java.util.List;

public class TokenEstimator {

    public int estimateConversationTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }

    public int estimateMessageTokens(Message msg) {
        int tokens = 4;
        
        if (msg.getContent() != null) {
            tokens += estimateTextTokens(msg.getContent());
        }
        
        if (msg.getToolCalls() != null) {
            for (ToolCall tc : msg.getToolCalls()) {
                if (tc.getFunction() != null && tc.getFunction().getArguments() != null) {
                    tokens += estimateTextTokens(tc.getFunction().getArguments());
                }
            }
        }
        
        return tokens;
    }

    public int estimateTextTokens(String text) {
        if (text == null) return 0;
        
        int chineseChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        
        return chineseChars + otherChars / 4;
    }
}