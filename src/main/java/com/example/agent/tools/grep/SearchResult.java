package com.example.agent.tools.grep;

import java.util.Objects;

public class SearchResult {
    private final String filePath;
    private final int lineNumber;
    private final String lineContent;
    private final boolean isContext;

    public SearchResult(String filePath, int lineNumber, String lineContent) {
        this(filePath, lineNumber, lineContent, false);
    }

    public SearchResult(String filePath, int lineNumber, String lineContent, boolean isContext) {
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.lineNumber = lineNumber;
        this.lineContent = Objects.requireNonNull(lineContent, "lineContent must not be null");
        this.isContext = isContext;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getLineContent() {
        return lineContent;
    }

    public boolean isContext() {
        return isContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchResult that)) return false;
        return lineNumber == that.lineNumber
            && isContext == that.isContext
            && filePath.equals(that.filePath)
            && lineContent.equals(that.lineContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, lineNumber, lineContent, isContext);
    }

    @Override
    public String toString() {
        return (isContext ? "- " : "") + filePath + ":" + lineNumber + ": " + lineContent;
    }
}
