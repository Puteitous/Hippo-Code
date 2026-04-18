package com.example.agent.lsp.model;

public class TextDocumentIdentifier {

    private String uri;

    public TextDocumentIdentifier() {
    }

    public TextDocumentIdentifier(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return "TextDocumentIdentifier{uri='" + uri + "'}";
    }
}
