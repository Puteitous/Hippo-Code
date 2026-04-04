package com.example.agent.context;

import com.example.agent.llm.model.Message;
import java.util.List;

public interface TrimPolicy {
    
    List<Message> apply(List<Message> messages, int maxTokens, int maxMessages);
    
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
