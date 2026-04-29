package com.example.agent.tools.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ReadFileTool 的参数规范化器
 */
public class ReadFileNormalizer implements ToolParamNormalizer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_MAX_TOKENS = 4000;
    
    @Override
    public JsonNode normalize(JsonNode arguments) {
        ObjectNode normalized = objectMapper.createObjectNode();
        
        // 规范化 path 参数
        if (arguments.has("path")) {
            String path = arguments.get("path").asText();
            // 规范化路径：去除首尾空格，统一斜杠
            path = path.trim().replace("\\", "/");
            normalized.put("path", path);
        }
        
        // 设置 max_tokens 默认值
        if (arguments.has("max_tokens") && !arguments.get("max_tokens").isNull()) {
            normalized.put("max_tokens", arguments.get("max_tokens").asInt());
        } else {
            normalized.put("max_tokens", DEFAULT_MAX_TOKENS);
        }
        
        return normalized;
    }
}
