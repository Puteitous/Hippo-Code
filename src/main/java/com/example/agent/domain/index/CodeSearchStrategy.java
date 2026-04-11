package com.example.agent.domain.index;

import java.util.List;

public interface CodeSearchStrategy {
    void buildIndex();
    List<SearchResult> search(String query, int maxResults);
    int getIndexSize();
}
