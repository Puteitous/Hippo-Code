package com.example.agent.orchestrator;

import com.example.agent.llm.model.ToolCall;
import com.example.agent.orchestrator.analyzer.DependencyAnalyzer;
import com.example.agent.orchestrator.analyzer.RuleBasedAnalyzer;
import com.example.agent.orchestrator.executor.DagExecutor;
import com.example.agent.orchestrator.model.ToolExecutionPlan;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.tools.concurrent.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ToolOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ToolOrchestrator.class);

    private final ConcurrentToolExecutor fallbackExecutor;
    private final DependencyAnalyzer analyzer;
    private final DagExecutor dagExecutor;

    private boolean enabled = true;
    private boolean dependencyAnalysisEnabled = true;
    private boolean transactionEnabled = true;

    public ToolOrchestrator(ConcurrentToolExecutor fallbackExecutor) {
        this.fallbackExecutor = fallbackExecutor;
        this.analyzer = new RuleBasedAnalyzer();
        this.dagExecutor = new DagExecutor(fallbackExecutor);
    }

    public List<ToolExecutionResult> executeConcurrently(List<ToolCall> toolCalls) {
        if (!enabled) {
            logger.debug("编排引擎已禁用，使用并发执行");
            return fallbackExecutor.executeConcurrently(toolCalls);
        }

        if (toolCalls == null || toolCalls.size() <= 1) {
            return fallbackExecutor.executeConcurrently(toolCalls);
        }

        try {
            if (dependencyAnalysisEnabled) {
                ToolExecutionPlan plan = analyzer.analyze(toolCalls);
                return dagExecutor.execute(plan, toolCalls);
            } else {
                logger.debug("依赖分析已禁用，使用并发执行");
                return fallbackExecutor.executeConcurrently(toolCalls);
            }
        } catch (Exception e) {
            logger.warn("编排引擎异常，降级到并发执行: {}", e.getMessage());
            return fallbackExecutor.executeConcurrently(toolCalls);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("编排引擎: {}", enabled ? "启用" : "禁用");
    }

    public boolean isDependencyAnalysisEnabled() {
        return dependencyAnalysisEnabled;
    }

    public void setDependencyAnalysisEnabled(boolean dependencyAnalysisEnabled) {
        this.dependencyAnalysisEnabled = dependencyAnalysisEnabled;
    }

    public boolean isTransactionEnabled() {
        return transactionEnabled;
    }

    public void setTransactionEnabled(boolean transactionEnabled) {
        this.transactionEnabled = transactionEnabled;
    }

    public String getStats() {
        return String.format("ToolOrchestrator{enabled=%s, analysisEnabled=%s, transactionEnabled=%s}",
                enabled, dependencyAnalysisEnabled, transactionEnabled);
    }
}
