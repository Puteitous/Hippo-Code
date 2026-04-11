package com.example.agent.domain.index;

import com.example.agent.domain.index.search.InvertedIndexSearch;
import com.example.agent.domain.index.search.SimpleKeywordSearch;

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
