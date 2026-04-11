package com.example.agent.domain.index;

public class SearchResult {
    public final String filePath;
    public final String preview;
    public final double score;

    public SearchResult(String filePath, String preview, double score) {
        this.filePath = filePath;
        this.preview = preview;
        this.score = score;
    }
}
