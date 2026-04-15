package com.example.agent.memory;

import com.example.agent.context.TrimPolicy;
import com.example.agent.context.policy.SlidingWindowPolicy;
import com.example.agent.memory.classifier.MessageClassifier;
import com.example.agent.memory.classifier.RuleBasedClassifier;
import com.example.agent.memory.summarizer.SummaryGenerator;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemorySystem {

    private static final Logger logger = LoggerFactory.getLogger(MemorySystem.class);

    private final TokenEstimator tokenEstimator;
    private final PriorityTrimPolicy prioritizedPolicy;
    private final TrimPolicy fallbackPolicy;
    private final MessageClassifier classifier;
    private SummaryGenerator summarizer;

    private boolean enabled = true;
    private boolean summaryEnabled = true;

    public MemorySystem(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
        this.classifier = new RuleBasedClassifier();
        this.prioritizedPolicy = new PriorityTrimPolicy(tokenEstimator, true);
        this.fallbackPolicy = new SlidingWindowPolicy(tokenEstimator, 5);
    }

    public TrimPolicy getTrimPolicy() {
        if (!enabled) {
            logger.debug("优先级记忆已禁用，使用滑动窗口策略");
            return fallbackPolicy;
        }
        return prioritizedPolicy;
    }

    public PriorityTrimPolicy getPrioritizedTrimPolicy() {
        return prioritizedPolicy;
    }

    public TrimPolicy getFallbackPolicy() {
        return fallbackPolicy;
    }

    public MessageClassifier getClassifier() {
        return classifier;
    }

    public SummaryGenerator getSummarizer() {
        return summarizer;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("优先级记忆系统: {}", enabled ? "已启用" : "已禁用");
    }

    public void setSummaryEnabled(boolean summaryEnabled) {
        this.summaryEnabled = summaryEnabled;
        logger.info("摘要功能: {}", summaryEnabled ? "已启用" : "已禁用");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSummaryEnabled() {
        return summaryEnabled;
    }

    public String getStats() {
        return String.format(
                "MemorySystem{enabled=%s, policy=%s, classifier=%s}",
                enabled,
                enabled ? "PriorityTrimPolicy" : "SlidingWindowPolicy",
                classifier.getClass().getSimpleName()
        );
    }
}
