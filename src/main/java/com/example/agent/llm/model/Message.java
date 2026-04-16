package com.example.agent.llm.model;

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
    
    @JsonProperty("cache_control")
    private CacheControl cacheControl;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;
    
    private String name;

    public Message() {
    }

    public Message(String role, String content) {
        // 使用LLM标准role作为默认值，避免发送API不识别的"unknown"
        this.role = (role != null && !role.trim().isEmpty()) ? role.trim() : "user";
        this.content = content != null ? content : "";
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
        if (toolCalls != null && !toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }
        return message;
    }

    public static Message toolResult(String toolCallId, String name, String content) {
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            throw new IllegalArgumentException("toolCallId不能为null或空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name不能为null或空");
        }
        Message message = new Message("tool", content != null ? content : "");
        message.setToolCallId(toolCallId.trim());
        message.setName(name.trim());
        return message;
    }

    public boolean isSystem() {
        return "system".equals(role);
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }

    public boolean isTool() {
        return "tool".equals(role);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        // 使用LLM标准role作为默认值，避免发送API不识别的"unknown"
        this.role = (role != null && !role.trim().isEmpty()) ? role.trim() : "user";
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
    }

    public CacheControl getCacheControl() {
        return cacheControl;
    }

    public void setCacheControl(CacheControl cacheControl) {
        this.cacheControl = cacheControl;
    }

    public void enableEphemeralCache() {
        this.cacheControl = CacheControl.ephemeral();
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null) {
            this.toolCalls = null;
        } else {
            // 过滤掉null元素
            this.toolCalls = toolCalls.stream()
                .filter(tc -> tc != null)
                .collect(java.util.stream.Collectors.toList());
        }
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
                ", cacheControl=" + cacheControl +
                '}';
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CacheControl {
        private String type = "ephemeral";

        public CacheControl() {
        }

        public CacheControl(String type) {
            this.type = type;
        }

        public static CacheControl ephemeral() {
            return new CacheControl("ephemeral");
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type;
        }
    }
}