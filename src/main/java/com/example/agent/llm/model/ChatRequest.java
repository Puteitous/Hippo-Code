package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {

    private String model;
    private List<Message> messages;
    private List<Tool> tools;
    
    @JsonProperty("tool_choice")
    private Object toolChoice;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    private Double temperature;
    private Double topP;
    private Boolean stream;

    public ChatRequest() {
    }

    public ChatRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }

    public static ChatRequest of(String model, List<Message> messages) {
        return new ChatRequest(model, messages);
    }

    public ChatRequest tools(List<Tool> tools) {
        this.tools = tools;
        return this;
    }

    public ChatRequest toolChoiceAuto() {
        this.toolChoice = "auto";
        return this;
    }

    public ChatRequest toolChoiceNone() {
        this.toolChoice = "none";
        return this;
    }

    public ChatRequest toolChoiceRequired() {
        this.toolChoice = "required";
        return this;
    }

    public ChatRequest toolChoiceFunction(String functionName) {
        this.toolChoice = java.util.Map.of("type", "function", "function", java.util.Map.of("name", functionName));
        return this;
    }

    public ChatRequest maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public ChatRequest temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public ChatRequest topP(Double topP) {
        this.topP = topP;
        return this;
    }

    public ChatRequest stream(Boolean stream) {
        this.stream = stream;
        return this;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}
