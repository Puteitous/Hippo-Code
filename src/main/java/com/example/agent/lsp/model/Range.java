package com.example.agent.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Range {

    private Position start;
    private Position end;

    public Range() {
    }

    public Range(Position start, Position end) {
        this.start = start;
        this.end = end;
    }

    public Position getStart() {
        return start;
    }

    public void setStart(Position start) {
        this.start = start;
    }

    public Position getEnd() {
        return end;
    }

    public void setEnd(Position end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "Range{start=" + start + ", end=" + end + "}";
    }
}
