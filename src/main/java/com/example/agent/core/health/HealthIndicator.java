package com.example.agent.core.health;

public interface HealthIndicator {
    String getName();
    Health check();
}
