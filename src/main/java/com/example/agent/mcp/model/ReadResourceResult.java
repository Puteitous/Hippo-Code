package com.example.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadResourceResult {

    private List<ResourceContents> contents = new ArrayList<>();

    public ReadResourceResult() {
    }

    public List<ResourceContents> getContents() {
        return contents;
    }

    public void setContents(List<ResourceContents> contents) {
        this.contents = contents;
    }
}
