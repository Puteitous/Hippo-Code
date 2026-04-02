package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface ToolExecutor {
    
    String getName();
    
    String getDescription();
    
    String getParametersSchema();
    
    String execute(JsonNode arguments) throws ToolExecutionException;
}
