package com.example.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    private String role;
    private String content;
    
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;
    
    private String name;

    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public static Message assistantWithToolCalls(List<ToolCall> toolCalls) {
        Message message = new Message("assistant", null);
        message.setToolCalls(toolCalls);
        return message;
    }

    public static Message toolResult(String toolCallId, String name, String content) {
        Message message = new Message("tool", content);
        message.setToolCallId(toolCallId);
        message.setName(name);
        return message;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public void addToolCall(ToolCall toolCall) {
        if (this.toolCalls == null) {
            this.toolCalls = new ArrayList<>();
        }
        this.toolCalls.add(toolCall);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Message{" +
                "role='" + role + '\'' +
                ", content='" + (content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content) + '\'' +
                ", toolCalls=" + toolCalls +
                ", toolCallId='" + toolCallId + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}