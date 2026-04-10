package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private Usage usage;
    private List<Choice> choices;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Message getFirstMessage() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice firstChoice = choices.get(0);
        if (firstChoice == null) {
            return null;
        }
        return firstChoice.getMessage();
    }

    public boolean hasToolCalls() {
        Message message = getFirstMessage();
        if (message == null) {
            return false;
        }
        List<ToolCall> toolCalls = message.getToolCalls();
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        String content = getContent();
        return content != null && !content.isEmpty();
    }

    public String getContent() {
        Message message = getFirstMessage();
        return message != null ? message.getContent() : null;
    }

    public List<ToolCall> getToolCalls() {
        Message message = getFirstMessage();
        return message != null ? message.getToolCalls() : null;
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "id='" + id + '\'' +
                ", model='" + model + '\'' +
                ", usage=" + usage +
                ", choices=" + choices +
                '}';
    }
}
