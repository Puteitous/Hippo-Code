package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmTaskPlanner implements TaskPlanner {

    private static final Logger logger = LoggerFactory.getLogger(LlmTaskPlanner.class);
    private static final int DEFAULT_PRIORITY = 10;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private boolean enabled = true;

    public LlmTaskPlanner(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ExecutionPlan plan(IntentResult intent, PlanningContext context) {
        logger.debug("使用LLM规划器处理意图: {}", intent.getType());

        try {
            String planJson = generatePlanFromLlm(intent, context);
            if (planJson == null || planJson.isEmpty()) {
                logger.warn("LLM返回空计划，使用默认计划");
                return createDefaultPlan(intent);
            }

            return parsePlanFromJson(intent, planJson);
        } catch (Exception e) {
            logger.error("LLM规划失败: {}", e.getMessage());
            return createDefaultPlan(intent);
        }
    }

    private String generatePlanFromLlm(IntentResult intent, PlanningContext context) {
        List<Message> messages = new ArrayList<>();

        messages.add(Message.system(PlanningPrompts.getSystemPrompt()));

        String userPrompt = PlanningPrompts.buildPlanningPrompt(intent, context);
        messages.add(Message.user(userPrompt));

        try {
            ChatResponse response = llmClient.chat(messages);

            if (response != null && response.getContent() != null) {
                return extractJson(response.getContent());
            }

            return null;
        } catch (Exception e) {
            logger.error("LLM调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        content = content.trim();

        if (content.startsWith("```json")) {
            int start = content.indexOf('\n') + 1;
            int end = content.lastIndexOf("```");
            if (end > start) {
                return content.substring(start, end).trim();
            }
        } else if (content.startsWith("```")) {
            int start = content.indexOf('\n') + 1;
            int end = content.lastIndexOf("```");
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }

        int jsonStart = content.indexOf('{');
        int jsonEnd = content.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd + 1);
        }

        return content;
    }

    private ExecutionPlan parsePlanFromJson(IntentResult intent, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            ExecutionStrategy strategy = parseStrategy(root.get("strategy"));

            List<ExecutionStep> steps = parseSteps(root.get("steps"));

            if (steps.isEmpty()) {
                logger.warn("解析后步骤为空，使用默认计划");
                return createDefaultPlan(intent);
            }

            return ExecutionPlan.builder()
                    .intent(intent)
                    .strategy(strategy)
                    .steps(steps)
                    .build();

        } catch (Exception e) {
            logger.error("解析计划JSON失败: {}", e.getMessage());
            return createDefaultPlan(intent);
        }
    }

    private ExecutionStrategy parseStrategy(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return ExecutionStrategy.SEQUENTIAL;
        }

        try {
            return ExecutionStrategy.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            return ExecutionStrategy.SEQUENTIAL;
        }
    }

    private List<ExecutionStep> parseSteps(JsonNode node) {
        List<ExecutionStep> steps = new ArrayList<>();

        if (node == null || !node.isArray()) {
            return steps;
        }

        int index = 1;
        for (JsonNode stepNode : node) {
            try {
                ExecutionStep step = parseStep(stepNode, index++);
                if (step != null) {
                    steps.add(step);
                }
            } catch (Exception e) {
                logger.warn("解析步骤失败: {}", e.getMessage());
            }
        }

        return steps;
    }

    private ExecutionStep parseStep(JsonNode node, int index) {
        if (node == null) {
            return null;
        }

        String id = node.has("id") ? node.get("id").asText() : "step-" + index;

        StepType type = parseStepType(node.get("type"));

        String description = node.has("description") ? node.get("description").asText() : "";

        String toolName = node.has("toolName") ? node.get("toolName").asText() : null;

        Map<String, Object> arguments = parseArguments(node.get("arguments"));

        List<String> dependencies = parseDependencies(node.get("dependencies"));

        return ExecutionStep.builder()
                .id(id)
                .type(type)
                .description(description)
                .toolName(toolName)
                .arguments(arguments)
                .dependencies(dependencies)
                .build();
    }

    private StepType parseStepType(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return StepType.LLM_CALL;
        }

        try {
            return StepType.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            return StepType.LLM_CALL;
        }
    }

    private Map<String, Object> parseArguments(JsonNode node) {
        Map<String, Object> args = new HashMap<>();

        if (node == null || !node.isObject()) {
            return args;
        }

        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                args.put(entry.getKey(), value.asText());
            } else if (value.isInt()) {
                args.put(entry.getKey(), value.asInt());
            } else if (value.isLong()) {
                args.put(entry.getKey(), value.asLong());
            } else if (value.isDouble()) {
                args.put(entry.getKey(), value.asDouble());
            } else if (value.isBoolean()) {
                args.put(entry.getKey(), value.asBoolean());
            } else {
                args.put(entry.getKey(), value.toString());
            }
        });

        return args;
    }

    private List<String> parseDependencies(JsonNode node) {
        List<String> deps = new ArrayList<>();

        if (node == null || !node.isArray()) {
            return deps;
        }

        for (JsonNode dep : node) {
            if (dep.isTextual()) {
                deps.add(dep.asText());
            }
        }

        return deps;
    }

    private ExecutionPlan createDefaultPlan(IntentResult intent) {
        ExecutionStep step = ExecutionStep.builder()
                .id("step-default")
                .type(StepType.LLM_CALL)
                .description("处理用户请求")
                .build();

        return ExecutionPlan.builder()
                .intent(intent)
                .strategy(ExecutionStrategy.SEQUENTIAL)
                .step(step)
                .build();
    }

    @Override
    public boolean supports(IntentType type) {
        return type != IntentType.UNKNOWN;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
