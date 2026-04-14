package com.example.agent.tools.concurrent;

import com.example.agent.llm.model.ToolCall;
import com.example.agent.tools.ToolExecutionException;
import com.example.agent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentToolExecutor {

    private final ToolRegistry toolRegistry;
    private final FileLockManager lockManager;
    private final ObjectMapper objectMapper;

    public ConcurrentToolExecutor(ToolRegistry toolRegistry) {
        this(toolRegistry, new ObjectMapper());
    }

    public ConcurrentToolExecutor(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.lockManager = FileLockManager.getInstance();
        this.objectMapper = objectMapper;
    }

    public List<ToolExecutionResult> executeConcurrently(List<ToolCall> toolCalls) {
        List<ToolExecutionResult> results = new ArrayList<>();
        
        if (toolCalls == null || toolCalls.isEmpty()) {
            return results;
        }

        if (toolCalls.size() == 1) {
            results.add(executeSingle(toolCalls.get(0), 0));
            return results;
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ToolExecutionResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < toolCalls.size(); i++) {
                int index = i;
                ToolCall call = toolCalls.get(i);
                
                futures.add(executor.submit(() -> executeSingle(call, index)));
            }

            for (Future<ToolExecutionResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    results.add(ToolExecutionResult.builder()
                            .success(false)
                            .errorMessage("执行失败: " + e.getMessage())
                            .build());
                }
            }
        }

        results.sort(Comparator.comparingInt(ToolExecutionResult::getIndex));
        
        return results;
    }

    private ToolExecutionResult executeSingle(ToolCall toolCall, int index) {
        if (toolCall.getFunction() == null || toolCall.getFunction().getName() == null 
            || toolCall.getFunction().getName().isEmpty()) {
            return ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCall.getId())
                    .toolName("")
                    .success(false)
                    .errorMessage("无效的工具调用: 工具名称为空")
                    .executionTimeMs(0)
                    .build();
        }
        
        String toolName = toolCall.getFunction().getName();
        String arguments = toolCall.getFunction().getArguments();
        String toolCallId = toolCall.getId();

        if (arguments == null || arguments.isEmpty()) {
            arguments = "{}";
        }

        long startTime = System.currentTimeMillis();
        
        try {
            JsonNode argumentsNode = objectMapper.readTree(arguments);
            String result = toolRegistry.execute(toolName, arguments);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .result(result)
                    .success(true)
                    .executionTimeMs(executionTime)
                    .build();
                    
        } catch (ToolExecutionException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("参数解析失败: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    public ExecutionStats getExecutionStats(List<ToolExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return new ExecutionStats(0, 0, 0, 0);
        }
        
        int totalCount = results.size();
        int successCount = 0;
        int failureCount = 0;
        long totalTime = 0;
        
        for (ToolExecutionResult result : results) {
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
            totalTime += result.getExecutionTimeMs();
        }
        
        return new ExecutionStats(totalCount, successCount, failureCount, totalTime);
    }

    public static class ExecutionStats {
        private final int totalCount;
        private final int successCount;
        private final int failureCount;
        private final long totalExecutionTimeMs;

        public ExecutionStats(int totalCount, int successCount, int failureCount, long totalExecutionTimeMs) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalExecutionTimeMs = totalExecutionTimeMs;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public long getTotalExecutionTimeMs() {
            return totalExecutionTimeMs;
        }

        public long getAverageExecutionTimeMs() {
            return totalCount > 0 ? totalExecutionTimeMs / totalCount : 0;
        }

        @Override
        public String toString() {
            return "ExecutionStats{" +
                    "totalCount=" + totalCount +
                    ", successCount=" + successCount +
                    ", failureCount=" + failureCount +
                    ", totalExecutionTimeMs=" + totalExecutionTimeMs +
                    '}';
        }
    }
}
