package com.example.agent.tools.grep;

import com.example.agent.tools.ToolExecutionException;

import java.util.List;

public interface GrepBackend {

    String getName();

    List<SearchResult> search(GrepOptions options) throws ToolExecutionException;

    default boolean isAvailable() {
        return true;
    }
}
