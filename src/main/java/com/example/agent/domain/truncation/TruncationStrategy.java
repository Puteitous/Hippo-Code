package com.example.agent.domain.truncation;

public interface TruncationStrategy {

    String truncate(String content, int maxTokens);

    boolean supports(String contentType);
}
