package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

public interface ToolExecutor {
    
    String getName();
    
    String getDescription();
    
    String getParametersSchema();
    
    String execute(JsonNode arguments) throws ToolExecutionException;

    default List<String> getAffectedPaths(JsonNode arguments) {
        List<String> paths = doGetAffectedPaths(arguments);
        return paths != null ? paths : Collections.emptyList();
    }
    
    default List<String> doGetAffectedPaths(JsonNode arguments) {
        return Collections.emptyList();
    }

    default boolean requiresFileLock() {
        return false;
    }
}
