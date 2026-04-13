package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListResourcesResult {

    private List<McpResource> resources = new ArrayList<>();

    public ListResourcesResult() {
    }

    public List<McpResource> getResources() {
        return resources;
    }

    public void setResources(List<McpResource> resources) {
        this.resources = resources;
    }
}
