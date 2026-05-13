package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;

public interface ToolExecutor {
    
    String getName();
    
    String getDescription();
    
    String getParametersSchema();
    
    String execute(JsonNode arguments) throws ToolExecutionException;

    default List<String> getAffectedPaths(JsonNode arguments) {
        return Collections.emptyList();
    }

    default boolean requiresFileLock() {
        return false;
    }

    default boolean shouldRunInBackground() {
        return true;
    }
}
