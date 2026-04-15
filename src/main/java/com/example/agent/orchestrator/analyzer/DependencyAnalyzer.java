package com.example.agent.orchestrator.analyzer;

import com.example.agent.llm.model.ToolCall;
import com.example.agent.orchestrator.model.ToolExecutionPlan;

import java.util.List;

public interface DependencyAnalyzer {

    ToolExecutionPlan analyze(List<ToolCall> toolCalls);
}
