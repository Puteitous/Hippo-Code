package com.example.agent.tools.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具参数规范化器接口
 * 用于在验证前对参数进行标准化处理
 */
public interface ToolParamNormalizer {
    
    /**
     * 规范化参数
     *
     * @param arguments 原始参数
     * @return 规范化后的参数
     */
    JsonNode normalize(JsonNode arguments);
    
    /**
     * 默认规范化器（不做任何处理）
     */
    ToolParamNormalizer DEFAULT = arguments -> arguments;
}
