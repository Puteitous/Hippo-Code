package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.model.Message;

import java.util.List;

public interface TaskPlanner {

    ExecutionPlan plan(IntentResult intent, PlanningContext context);

    default boolean supports(IntentType type) {
        return true;
    }

    default int getPriority() {
        return 0;
    }

    default String getName() {
        return this.getClass().getSimpleName();
    }

    default boolean isEnabled() {
        return true;
    }
}
