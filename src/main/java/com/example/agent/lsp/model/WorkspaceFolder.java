package com.example.agent.lsp.model;

public class WorkspaceFolder {

    private String uri;
    private String name;

    public WorkspaceFolder() {
    }

    public WorkspaceFolder(String uri, String name) {
        this.uri = uri;
        this.name = name;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "WorkspaceFolder{uri='" + uri + "', name='" + name + "'}";
    }
}
