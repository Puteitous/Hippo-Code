package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListPromptsResult {

    private List<McpPrompt> prompts = new ArrayList<>();

    public ListPromptsResult() {
    }

    public List<McpPrompt> getPrompts() {
        return prompts;
    }

    public void setPrompts(List<McpPrompt> prompts) {
        this.prompts = prompts;
    }
}
