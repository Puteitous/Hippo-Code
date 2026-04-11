package com.example.agent.context.memory;

import com.example.agent.context.memory.SearchResult;
import java.util.List;

public interface CodeSearchStrategy {
    void buildIndex();
    List<SearchResult> search(String query, int maxResults);
    int getIndexSize();
}
