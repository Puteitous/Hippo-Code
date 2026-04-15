package com.example.agent.memory.summarizer;

import com.example.agent.llm.model.Message;
import com.example.agent.memory.model.PrioritizedMessage;

import java.util.List;

public interface SummaryGenerator {

    Message generate(List<PrioritizedMessage> messages);
}
