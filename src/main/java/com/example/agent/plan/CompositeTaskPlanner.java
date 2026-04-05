package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class CompositeTaskPlanner implements TaskPlanner {

    private static final Logger logger = LoggerFactory.getLogger(CompositeTaskPlanner.class);

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.50;

    private final List<TaskPlanner> planners;
    private final SimpleTaskPlanner simplePlanner;
    private final LlmTaskPlanner llmPlanner;
    private boolean preferLlm = false;
    private boolean enabled = true;

    public CompositeTaskPlanner(SimpleTaskPlanner simplePlanner, LlmTaskPlanner llmPlanner) {
        this.simplePlanner = simplePlanner;
        this.llmPlanner = llmPlanner;
        this.planners = Arrays.asList(llmPlanner, simplePlanner);
    }

    @Override
    public ExecutionPlan plan(IntentResult intent, PlanningContext context) {
        if (intent == null) {
            logger.debug("意图为空，使用默认计划");
            intent = IntentResult.builder()
                    .type(IntentType.UNKNOWN)
                    .confidence(0.0)
                    .build();
        }

        logger.debug("复合规划器处理意图: {}, 置信度: {}", intent.getType(), intent.getConfidence());

        if (shouldUseLlmPlanner(intent)) {
            logger.debug("使用LLM规划器");
            return llmPlanner.plan(intent, context);
        } else {
            logger.debug("使用简单规划器");
            return simplePlanner.plan(intent, context);
        }
    }

    private boolean shouldUseLlmPlanner(IntentResult intent) {
        if (preferLlm) {
            return true;
        }

        if (intent.getConfidence() < LOW_CONFIDENCE_THRESHOLD) {
            return true;
        }

        if (isComplexIntent(intent)) {
            return true;
        }

        return false;
    }

    private boolean isComplexIntent(IntentResult intent) {
        IntentType type = intent.getType();

        if (type == IntentType.CODE_MODIFICATION) {
            return hasMultipleEntities(intent);
        }

        if (type == IntentType.DEBUGGING) {
            return true;
        }

        if (type == IntentType.PROJECT_ANALYSIS) {
            return true;
        }

        return false;
    }

    private boolean hasMultipleEntities(IntentResult intent) {
        return intent.getEntities().size() > 2;
    }

    @Override
    public boolean supports(IntentType type) {
        return planners.stream().anyMatch(p -> p.supports(type));
    }

    @Override
    public int getPriority() {
        return planners.stream()
                .mapToInt(TaskPlanner::getPriority)
                .max()
                .orElse(0);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPreferLlm(boolean preferLlm) {
        this.preferLlm = preferLlm;
    }

    public boolean isPreferLlm() {
        return preferLlm;
    }

    public SimpleTaskPlanner getSimplePlanner() {
        return simplePlanner;
    }

    public LlmTaskPlanner getLlmPlanner() {
        return llmPlanner;
    }
}
