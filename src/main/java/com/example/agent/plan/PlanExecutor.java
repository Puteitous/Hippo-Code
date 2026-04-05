package com.example.agent.plan;

public interface PlanExecutor {

    PlanResult execute(ExecutionPlan plan, ExecutionContext context);

    default boolean supports(ExecutionStrategy strategy) {
        return strategy == ExecutionStrategy.SEQUENTIAL;
    }

    default String getName() {
        return this.getClass().getSimpleName();
    }
}
