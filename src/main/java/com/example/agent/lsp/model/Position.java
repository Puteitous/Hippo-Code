package com.example.agent.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Position {

    private int line;
    private int character;

    public Position() {
    }

    public Position(int line, int character) {
        this.line = line;
        this.character = character;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getCharacter() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = character;
    }

    @Override
    public String toString() {
        return "Position{line=" + line + ", character=" + character + "}";
    }
}
