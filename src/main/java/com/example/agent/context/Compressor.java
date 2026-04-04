package com.example.agent.context;

import com.example.agent.llm.model.Message;

public interface Compressor {
    
    Message compress(Message message, int maxTokens);
    
    boolean supports(Message message);
    
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
