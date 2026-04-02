package com.example.agent.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionCall {

    private String name;
    private String arguments;

    public FunctionCall() {
    }

    public FunctionCall(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return "FunctionCall{" +
                "name='" + name + '\'' +
                ", arguments='" + arguments + '\'' +
                '}';
    }
}
