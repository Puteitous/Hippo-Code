package com.example.agent.service;

import java.util.List;

import com.example.agent.llm.model.Message;

public interface TokenEstimator {

    int estimateConversationTokens(List<Message> messages);

    int estimateMessageTokens(Message msg);

    int estimateTextTokens(String text);
}
