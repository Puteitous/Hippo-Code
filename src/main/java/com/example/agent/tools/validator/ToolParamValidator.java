package com.example.agent.tools.validator;

import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * 工具参数验证器接口
 * 用于实现工具特定的验证逻辑
 */
public interface ToolParamValidator {
    
    /**
     * 验证工具参数
     *
     * @param arguments JSON 参数节点
     * @throws ToolExecutionException 如果验证失败
     */
    void validateParameters(JsonNode arguments) throws ToolExecutionException;
}
