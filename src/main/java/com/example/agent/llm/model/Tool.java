package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tool {

    private String type;
    private FunctionDefinition function;

    public Tool() {
        this.type = "function";
    }

    public Tool(FunctionDefinition function) {
        this.type = "function";
        this.function = function;
    }

    public static Tool of(String name, String description) {
        return new Tool(new FunctionDefinition(name, description));
    }

    public static Tool of(String name, String description, Map<String, Object> parameters) {
        return new Tool(new FunctionDefinition(name, description, parameters));
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public void setFunction(FunctionDefinition function) {
        this.function = function;
    }

    @Override
    public String toString() {
        return "Tool{" +
                "type='" + type + '\'' +
                ", function=" + function +
                '}';
    }
}
