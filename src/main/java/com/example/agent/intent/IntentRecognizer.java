package com.example.agent.intent;

import com.example.agent.llm.model.Message;

import java.util.List;

public interface IntentRecognizer {

    IntentResult recognize(String userInput);

    IntentResult recognize(String userInput, List<Message> context);

    default String getName() {
        return this.getClass().getSimpleName();
    }

    default boolean isEnabled() {
        return true;
    }
}
