package com.example.agent.memory.classifier;

import com.example.agent.llm.model.Message;
import com.example.agent.memory.model.PrioritizedMessage;

public interface MessageClassifier {

    PrioritizedMessage classify(Message message);
}
