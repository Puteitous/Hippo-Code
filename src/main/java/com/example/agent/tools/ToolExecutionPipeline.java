package com.example.agent.tools;

import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.example.agent.tools.normalizer.ToolParamNormalizer;
import com.example.agent.tools.stats.JsonRepairStats;
import com.example.agent.tools.stats.ToolExecutionStats;
import com.example.agent.tools.validator.ToolParamValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具执行管道 - 统一处理工具调用的各个阶段
 * Phase 1: JSON 解析和修复
 * Phase 2: 参数规范化（工具特定）
 * Phase 3: 参数验证（基础验证 + 工具特定验证）
 * Phase 4: 执行工具
 */
public class ToolExecutionPipeline {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionPipeline.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final ToolExecutor toolExecutor;
    private final ToolParamNormalizer normalizer;
    private final ToolParamValidator validator;
    private final ToolExecutionStats executionStats;
    private final JsonRepairStats jsonRepairStats;
    
    public ToolExecutionPipeline(ToolExecutor toolExecutor) {
        this(toolExecutor, ToolParamNormalizer.DEFAULT, null, null, null);
    }
    
    public ToolExecutionPipeline(ToolExecutor toolExecutor, ToolParamNormalizer normalizer) {
        this(toolExecutor, normalizer, null, null, null);
    }
    
    public ToolExecutionPipeline(ToolExecutor toolExecutor, ToolParamNormalizer normalizer, ToolParamValidator validator) {
        this(toolExecutor, normalizer, validator, null, null);
    }
    
    public ToolExecutionPipeline(ToolExecutor toolExecutor, ToolParamNormalizer normalizer, ToolParamValidator validator, ToolExecutionStats executionStats, JsonRepairStats jsonRepairStats) {
        this.toolExecutor = toolExecutor;
        this.normalizer = normalizer;
        this.validator = validator;
        this.executionStats = executionStats;
        this.jsonRepairStats = jsonRepairStats;
    }
    
    /**
     * 执行工具调用管道
     * 
     * @param toolCallId 工具调用 ID
     * @param arguments JSON 参数
     * @param index 执行索引
     * @return 执行结果
     */
    public ToolExecutionResult execute(String toolCallId, String arguments, int index) {
        long startTime = System.currentTimeMillis();
        String toolName = toolExecutor.getName();
        
        try {
            // Phase 1: JSON 解析和修复（优雅降级）
            JsonNode parsedArguments = phase1_parseAndFix(arguments, toolName);
            
            // Phase 2: 参数规范化（工具特定）
            JsonNode normalizedArguments = phase2_normalize(parsedArguments, toolName);
            
            // Phase 3: 参数验证（优雅降级）
            String validationError = phase3_validate(toolCallId, normalizedArguments, toolName);
            if (validationError != null) {
                // 验证失败，返回友好错误，不中断流程
                long executionTime = System.currentTimeMillis() - startTime;
                
                if (executionStats != null) {
                    executionStats.recordCall(toolName, false, executionTime);
                }
                
                logStructuredExecution(toolCallId, toolName, false, executionTime, validationError);
                
                return ToolExecutionResult.builder()
                        .index(index)
                        .toolCallId(toolCallId)
                        .toolName(toolName)
                        .success(false)
                        .errorMessage(validationError)
                        .executionTimeMs(executionTime)
                        .build();
            }
            
            // Phase 4: 执行工具
            String result = phase4_execute(normalizedArguments);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录统计信息
            if (executionStats != null) {
                executionStats.recordCall(toolName, true, executionTime);
            }
            
            // 结构化日志
            logStructuredExecution(toolCallId, toolName, true, executionTime, null);
            
            return ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .success(true)
                    .result(result)
                    .executionTimeMs(executionTime)
                    .build();
                    
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 记录统计信息
            if (executionStats != null) {
                executionStats.recordCall(toolName, false, executionTime);
            }
            
            // 结构化日志
            logStructuredExecution(toolCallId, toolName, false, executionTime, e.getMessage());
            
            return ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }
    
    /**
     * Phase 1: JSON 解析和修复
     * - 解析 JSON 参数
     * - 应用 ToolArgumentSanitizer 进行修复
     * - 优雅降级：解析失败返回空对象，不中断流程
     */
    private JsonNode phase1_parseAndFix(String arguments, String toolName) {
        logger.debug("Phase 1: JSON 解析和修复");
        
        // 记录解析尝试
        if (jsonRepairStats != null) {
            jsonRepairStats.recordAttempt();
        }
        
        try {
            // 首先尝试标准解析
            return objectMapper.readTree(arguments);
            
        } catch (Exception e) {
            logger.warn("标准 JSON 解析失败，尝试修复：{}", e.getMessage());
            
            // 使用 ToolArgumentSanitizer 进行修复
            String fixedArguments = ToolArgumentSanitizer.fixJsonArguments(toolName, arguments);
            
            try {
                JsonNode fixedNode = objectMapper.readTree(fixedArguments);
                logger.info("✓ JSON 修复成功");
                
                // 记录修复统计
                if (jsonRepairStats != null) {
                    jsonRepairStats.recordRepair(toolName, e.getMessage().substring(0, Math.min(50, e.getMessage().length())));
                }
                if (executionStats != null) {
                    executionStats.recordJsonRepair(toolName);
                }
                
                return fixedNode;
                
            } catch (Exception e2) {
                logger.warn("JSON 修复失败，降级为空对象继续执行");
                
                // 记录修复失败
                if (jsonRepairStats != null) {
                    jsonRepairStats.recordFailure(toolName);
                }
                
                // 优雅降级：返回空对象，让业务逻辑处理
                return objectMapper.createObjectNode();
            }
        }
    }
    
    /**
     * Phase 2: 参数规范化（工具特定）
     * - 应用工具特定的规范化器
     * - 设置默认值
     * - 标准化格式
     */
    private JsonNode phase2_normalize(JsonNode arguments, String toolName) {
        logger.debug("Phase 2: 参数规范化");
        
        // 记录规范化统计
        if (executionStats != null) {
            executionStats.recordNormalization(toolName);
        }
        
        return normalizer.normalize(arguments);
    }
    
    /**
     * Phase 3: 参数验证
     * - 基础验证：检查必需参数
     * - 工具特定验证（如果提供了 validator）
     * - 优雅降级：验证失败返回错误信息，不中断流程
     */
    private String phase3_validate(String toolCallId, JsonNode arguments, String toolName) {
        logger.debug("Phase 3: 参数验证");
        
        // 基本验证：检查是否为空对象
        if (arguments == null || arguments.isMissingNode() || arguments.isEmpty()) {
            if (executionStats != null) {
                executionStats.recordValidationError(toolName);
            }
            logger.warn("参数为空，降级为友好错误提示");
            return String.format("缺少必需参数。请提供完整的参数，例如：查看工具文档了解所需参数");
        }
        
        // 工具特定验证
        if (validator != null) {
            try {
                validator.validateParameters(arguments);
            } catch (ToolExecutionException e) {
                if (executionStats != null) {
                    executionStats.recordValidationError(toolName);
                }
                logger.warn("参数验证失败，降级为友好错误提示：{}", e.getMessage());
                return e.getMessage();
            }
        }
        
        return null; // 验证通过
    }
    
    /**
     * Phase 4: 执行工具
     * - 调用工具的 execute 方法
     * - 处理执行结果
     */
    private String phase4_execute(JsonNode arguments) throws ToolExecutionException {
        logger.debug("Phase 4: 执行工具");
        return toolExecutor.execute(arguments);
    }
    
    /**
     * 结构化日志记录
     */
    private void logStructuredExecution(String toolCallId, String toolName, boolean success, long executionTimeMs, String errorMessage) {
        if (success) {
            logger.info("[TOOL_EXEC] tool_call_id={} tool_name={} status=SUCCESS execution_time_ms={}",
                    toolCallId, toolName, executionTimeMs);
        } else {
            logger.error("[TOOL_EXEC] tool_call_id={} tool_name={} status=FAILED execution_time_ms={} error=\"{}\"",
                    toolCallId, toolName, executionTimeMs, truncate(errorMessage, 200));
        }
    }
    
    /**
     * 截断字符串用于日志记录
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * 获取执行统计对象
     */
    public ToolExecutionStats getExecutionStats() {
        return executionStats;
    }
    
    /**
     * 获取 JSON 修复统计对象
     */
    public JsonRepairStats getJsonRepairStats() {
        return jsonRepairStats;
    }
}
