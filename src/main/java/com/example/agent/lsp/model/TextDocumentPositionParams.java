package com.example.agent.lsp.model;

public class TextDocumentPositionParams {

    private TextDocumentIdentifier textDocument;
    private Position position;

    public TextDocumentPositionParams() {
    }

    public TextDocumentPositionParams(TextDocumentIdentifier textDocument, Position position) {
        this.textDocument = textDocument;
        this.position = position;
    }

    public TextDocumentIdentifier getTextDocument() {
        return textDocument;
    }

    public void setTextDocument(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "TextDocumentPositionParams{textDocument=" + textDocument + ", position=" + position + "}";
    }
}
