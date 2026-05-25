package com.example.agent.tools.grep;

import java.nio.file.Path;
import java.util.Objects;

public class GrepOptions {

    public enum OutputMode {
        CONTENT,
        FILES_WITH_MATCHES,
        COUNT
    }

    private final String pattern;
    private final boolean caseSensitive;
    private final String filePattern;
    private final Path searchPath;
    private final int maxResults;
    private final OutputMode outputMode;
    private final int contextBefore;
    private final int contextAfter;
    private final boolean multiline;
    private final int offset;

    private GrepOptions(Builder builder) {
        this.pattern = builder.pattern;
        this.caseSensitive = builder.caseSensitive;
        this.filePattern = builder.filePattern;
        this.searchPath = builder.searchPath;
        this.maxResults = builder.maxResults;
        this.outputMode = builder.outputMode;
        this.contextBefore = builder.contextBefore;
        this.contextAfter = builder.contextAfter;
        this.multiline = builder.multiline;
        this.offset = builder.offset;
    }

    public String getPattern() { return pattern; }
    public boolean isCaseSensitive() { return caseSensitive; }
    public String getFilePattern() { return filePattern; }
    public Path getSearchPath() { return searchPath; }
    public int getMaxResults() { return maxResults; }
    public OutputMode getOutputMode() { return outputMode; }
    public int getContextBefore() { return contextBefore; }
    public int getContextAfter() { return contextAfter; }
    public boolean isMultiline() { return multiline; }
    public int getOffset() { return offset; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String pattern;
        private boolean caseSensitive = false;
        private String filePattern;
        private Path searchPath;
        private int maxResults = 100;
        private OutputMode outputMode = OutputMode.CONTENT;
        private int contextBefore;
        private int contextAfter;
        private boolean multiline;
        private int offset;

        public Builder pattern(String pattern) {
            this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
            return this;
        }

        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder filePattern(String filePattern) {
            this.filePattern = filePattern;
            return this;
        }

        public Builder searchPath(Path searchPath) {
            this.searchPath = searchPath;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = Math.max(1, Math.min(500, maxResults));
            return this;
        }

        public Builder outputMode(OutputMode outputMode) {
            this.outputMode = outputMode;
            return this;
        }

        public Builder contextBefore(int contextBefore) {
            this.contextBefore = Math.max(0, contextBefore);
            return this;
        }

        public Builder contextAfter(int contextAfter) {
            this.contextAfter = Math.max(0, contextAfter);
            return this;
        }

        public Builder multiline(boolean multiline) {
            this.multiline = multiline;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = Math.max(0, offset);
            return this;
        }

        public GrepOptions build() {
            if (pattern == null || pattern.trim().isEmpty()) {
                throw new IllegalStateException("pattern must not be null or empty");
            }
            return new GrepOptions(this);
        }
    }
}
