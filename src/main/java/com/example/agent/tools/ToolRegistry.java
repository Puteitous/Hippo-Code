package com.example.agent.tools;

import com.example.agent.llm.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, ToolExecutor> executors = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolRegistry() {
    }

    public ToolRegistry register(ToolExecutor executor) {
        executors.put(executor.getName(), executor);
        return this;
    }

    public ToolExecutor getExecutor(String name) {
        return executors.get(name);
    }

    public boolean hasTool(String name) {
        return executors.containsKey(name);
    }

    public List<Tool> toTools() {
        List<Tool> tools = new ArrayList<>();
        for (ToolExecutor executor : executors.values()) {
            try {
                JsonNode schema = objectMapper.readTree(executor.getParametersSchema());
                Map<String, Object> parameters = objectMapper.convertValue(schema, Map.class);
                tools.add(Tool.of(executor.getName(), executor.getDescription(), parameters));
            } catch (Exception e) {
                System.err.println("Failed to parse schema for tool: " + executor.getName());
            }
        }
        return tools;
    }

    public String execute(String toolName, String argumentsJson) throws ToolExecutionException {
        ToolExecutor executor = executors.get(toolName);
        if (executor == null) {
            throw new ToolExecutionException("Unknown tool: " + toolName);
        }

        try {
            JsonNode arguments = objectMapper.readTree(argumentsJson);
            return executor.execute(arguments);
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to parse arguments: " + e.getMessage(), e);
        }
    }
}
