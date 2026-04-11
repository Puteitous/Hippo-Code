package com.example.agent.context.index;

import java.util.List;

public interface CodeSearchStrategy {
    void buildIndex();
    List<SearchResult> search(String query, int maxResults);
    int getIndexSize();
}
