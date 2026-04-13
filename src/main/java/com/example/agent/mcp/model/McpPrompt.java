package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class McpPrompt {

    private String name;
    private String description;
    private List<PromptArgument> arguments = new ArrayList<>();

    public McpPrompt() {
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

    public List<PromptArgument> getArguments() {
        return arguments;
    }

    public void setArguments(List<PromptArgument> arguments) {
        this.arguments = arguments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptArgument {
        private String name;
        private String description;
        private boolean required;

        public PromptArgument() {
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

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}
