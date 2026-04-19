package com.example.agent.context;

import com.example.agent.context.budget.BudgetListener;
import com.example.agent.context.budget.BudgetThreshold;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class TokenBudget {

    private final int maxTokens;
    private int currentTokens;
    private final Set<BudgetThreshold> triggeredThresholds;
    private final List<BudgetListener> listeners;

    public TokenBudget(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        this.maxTokens = maxTokens;
        this.currentTokens = 0;
        this.triggeredThresholds = EnumSet.noneOf(BudgetThreshold.class);
        this.listeners = new ArrayList<>();
    }

    public void update(int newTokenCount) {
        this.currentTokens = newTokenCount;
        double ratio = getUsageRatio();

        listeners.forEach(listener -> listener.onBudgetUpdated(currentTokens, maxTokens, ratio));

        checkThresholds(ratio);

        if (currentTokens > maxTokens) {
            listeners.forEach(listener -> listener.onBudgetExceeded(currentTokens, maxTokens));
        }
    }

    public void addTokens(int tokensToAdd) {
        update(currentTokens + tokensToAdd);
    }

    private void checkThresholds(double ratio) {
        BudgetThreshold currentThreshold = BudgetThreshold.fromRatio(ratio);
        
        if (currentThreshold != null && !triggeredThresholds.contains(currentThreshold)) {
            triggeredThresholds.add(currentThreshold);
            notifyThreshold(currentThreshold);
        }
    }

    private void notifyThreshold(BudgetThreshold threshold) {
        listeners.forEach(listener -> 
            listener.onThresholdReached(threshold, currentTokens, maxTokens)
        );
    }

    public void addListener(BudgetListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(BudgetListener listener) {
        listeners.remove(listener);
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getCurrentTokens() {
        return currentTokens;
    }

    public double getUsageRatio() {
        return (double) currentTokens / maxTokens;
    }

    public boolean isThresholdTriggered(BudgetThreshold threshold) {
        return triggeredThresholds.contains(threshold);
    }

    public BudgetThreshold getHighestThreshold() {
        BudgetThreshold[] thresholds = BudgetThreshold.values();
        for (int i = thresholds.length - 1; i >= 0; i--) {
            if (triggeredThresholds.contains(thresholds[i])) {
                return thresholds[i];
            }
        }
        return null;
    }

    public void reset() {
        currentTokens = 0;
        triggeredThresholds.clear();
    }
}
