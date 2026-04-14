package com.example.agent.core.event;

public interface Event {
    long timestampMs = System.currentTimeMillis();

    default long getTimestampMs() {
        return timestampMs;
    }
}