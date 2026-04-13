package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GetPromptResult {

    private String description;
    private List<PromptMessage> messages = new ArrayList<>();

    public GetPromptResult() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PromptMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<PromptMessage> messages) {
        this.messages = messages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptMessage {
        private String role;
        private PromptContent content;

        public PromptMessage() {
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public PromptContent getContent() {
            return content;
        }

        public void setContent(PromptContent content) {
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptContent {
        private String type;
        private String text;

        public PromptContent() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
