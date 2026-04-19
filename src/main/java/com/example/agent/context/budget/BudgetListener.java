package com.example.agent.context.budget;

public interface BudgetListener {

    void onThresholdReached(BudgetThreshold threshold, int currentTokens, int maxTokens);

    default void onBudgetUpdated(int currentTokens, int maxTokens, double usageRatio) {
    }

    default void onBudgetExceeded(int currentTokens, int maxTokens) {
    }
}
