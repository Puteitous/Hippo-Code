package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListToolsResult {

    private List<McpTool> tools = new ArrayList<>();

    public ListToolsResult() {
    }

    public List<McpTool> getTools() {
        return tools;
    }

    public void setTools(List<McpTool> tools) {
        this.tools = tools;
    }
}
