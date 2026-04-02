package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.HashMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionDefinition {

    private String name;
    private String description;
    private Map<String, Object> parameters;

    public FunctionDefinition() {
    }

    public FunctionDefinition(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public FunctionDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public static FunctionDefinition create(String name, String description) {
        return new FunctionDefinition(name, description);
    }

    public FunctionDefinition parameter(String key, Object value) {
        if (this.parameters == null) {
            this.parameters = new HashMap<>();
        }
        this.parameters.put(key, value);
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "FunctionDefinition{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
