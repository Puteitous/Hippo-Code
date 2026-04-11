package com.example.agent.context.memory;

import com.example.agent.context.memory.search.InvertedIndexSearch;
import com.example.agent.context.memory.search.SimpleKeywordSearch;

public class SearchEngineFactory {
    public static CodeSearchStrategy getDefault() {
        return new InvertedIndexSearch();
    }

    public static CodeSearchStrategy invertedIndex() {
        return new InvertedIndexSearch();
    }

    public static CodeSearchStrategy keywordMatch() {
        return new SimpleKeywordSearch();
    }

    public static CodeSearchStrategy semanticSearch() {
        return getDefault();
    }

    public static CodeSearchStrategy hybridSearch() {
        return getDefault();
    }
}
