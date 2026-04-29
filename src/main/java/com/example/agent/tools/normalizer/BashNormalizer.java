package com.example.agent.tools.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BashTool 的参数规范化器
 */
public class BashNormalizer implements ToolParamNormalizer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT = 30;
    
    @Override
    public JsonNode normalize(JsonNode arguments) {
        ObjectNode normalized = objectMapper.createObjectNode();
        
        // 规范化 command 参数
        if (arguments.has("command")) {
            String command = arguments.get("command").asText().trim();
            normalized.put("command", command);
        }
        
        // 规范化 working_dir 参数
        if (arguments.has("working_dir") && !arguments.get("working_dir").isNull()) {
            String workingDir = arguments.get("working_dir").asText().trim();
            workingDir = workingDir.replace("\\", "/");
            normalized.put("working_dir", workingDir);
        }
        
        // 设置 timeout 默认值
        if (arguments.has("timeout") && !arguments.get("timeout").isNull()) {
            normalized.put("timeout", arguments.get("timeout").asInt());
        } else {
            normalized.put("timeout", DEFAULT_TIMEOUT);
        }
        
        return normalized;
    }
}
