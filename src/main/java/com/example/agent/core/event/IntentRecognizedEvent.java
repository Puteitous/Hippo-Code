package com.example.agent.core.event;

import com.example.agent.intent.IntentType;

public record IntentRecognizedEvent(
        IntentType intentType,
        double confidence,
        String userInput
) implements Event {}