package com.example.agent.context;

import com.example.agent.llm.model.Message;

public interface Compressor {
    
    Message compress(Message message, int maxTokens);
    
    boolean supports(Message message);
    
    default String compress(String content) {
        Message temp = Message.toolResult("temp", "temp", content);
        Message result = compress(temp, 1000);
        return result.getContent();
    }
    
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
