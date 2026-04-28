package com.example.agent.tools.concurrent;

import com.example.agent.core.logging.LoggingContext;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.progress.ToolExecutionCallback;
import com.example.agent.tools.ToolExecutor;
import com.example.agent.tools.ToolExecutionException;
import com.example.agent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final FileLockManager lockManager;
    private final ObjectMapper objectMapper;
    private final List<ToolExecutionCallback> callbacks = new CopyOnWriteArrayList<>();

    public ConcurrentToolExecutor(ToolRegistry toolRegistry) {
        this(toolRegistry, new ObjectMapper());
    }

    public ConcurrentToolExecutor(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.lockManager = FileLockManager.getInstance();
        this.objectMapper = objectMapper;
    }

    public void registerCallback(ToolExecutionCallback callback) {
        callbacks.add(callback);
    }

    public void removeCallback(ToolExecutionCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyToolStart(ToolCall toolCall, int index, int total) {
        for (ToolExecutionCallback callback : callbacks) {
            try {
                callback.onToolStart(toolCall, index, total);
            } catch (Exception e) {
                logger.warn("回调执行异常: {}", e.getMessage());
            }
        }
    }

    private void notifyToolComplete(ToolCall toolCall, ToolExecutionResult result, int index, int total) {
        for (ToolExecutionCallback callback : callbacks) {
            try {
                callback.onToolComplete(toolCall, result, index, total);
            } catch (Exception e) {
                logger.warn("回调执行异常: {}", e.getMessage());
            }
        }
    }

    public List<ToolExecutionResult> executeConcurrently(List<ToolCall> toolCalls) {
        List<ToolExecutionResult> results = new ArrayList<>();
        
        if (toolCalls == null || toolCalls.isEmpty()) {
            return results;
        }

        int total = toolCalls.size();
        List<ToolCall> backgroundTasks = new ArrayList<>();
        
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            String toolName = call.getFunction() != null ? call.getFunction().getName() : "";
            ToolExecutor executor = toolRegistry.getExecutor(toolName);
            
            if (executor != null && !executor.shouldRunInBackground()) {
                results.add(executeSingle(call, i, total));
            } else {
                backgroundTasks.add(call);
            }
        }

        if (!backgroundTasks.isEmpty()) {
            executeBackgroundTasks(backgroundTasks, results, total);
        }

        results.sort(Comparator.comparingInt(ToolExecutionResult::getIndex));
        
        return results;
    }

    private void executeBackgroundTasks(List<ToolCall> toolCalls, 
                                        List<ToolExecutionResult> results, 
                                        int total) {
        if (toolCalls.size() == 1) {
            results.add(executeSingle(toolCalls.get(0), 0, total));
            return;
        }

        ClassLoader classLoader = ToolRegistry.class.getClassLoader();
        try (var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .inheritInheritableThreadLocals(true)
                .factory())) {
            Map<Future<ToolExecutionResult>, Integer> futureIndexMap = new HashMap<>();
            
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCall call = toolCalls.get(i);
                final int index = i;
                Future<ToolExecutionResult> future = executor.submit(() -> {
                    Thread.currentThread().setContextClassLoader(classLoader);
                    return executeSingle(call, index, total);
                });
                futureIndexMap.put(future, index);
            }

            for (Map.Entry<Future<ToolExecutionResult>, Integer> entry : futureIndexMap.entrySet()) {
                try {
                    results.add(entry.getKey().get());
                } catch (Exception e) {
                    results.add(ToolExecutionResult.builder()
                            .index(entry.getValue())
                            .success(false)
                            .errorMessage("执行失败: " + e.getMessage())
                            .build());
                }
            }
        }
    }



    public ToolExecutionResult executeSingle(ToolCall toolCall, int index, int total) {
        Map<String, String> mdcSnapshot = LoggingContext.snapshot();
        
        if (toolCall.getFunction() == null || toolCall.getFunction().getName() == null 
            || toolCall.getFunction().getName().isEmpty()) {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCall.getId())
                    .toolName("")
                    .success(false)
                    .errorMessage("无效的工具调用: 工具名称为空")
                    .executionTimeMs(0)
                    .build();
            notifyToolComplete(toolCall, result, index, total);
            return result;
        }
        
        String toolName = toolCall.getFunction().getName();
        String arguments = toolCall.getFunction().getArguments();
        String toolCallId = toolCall.getId();

        if (arguments == null || arguments.isEmpty()) {
            arguments = "{}";
        }

        ToolExecutor executor = toolRegistry.getExecutor(toolName);
        if (executor != null && executor.shouldRunInBackground()) {
            notifyToolStart(toolCall, index, total);
        }
        long startTime = System.currentTimeMillis();
        
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ToolRegistry.class.getClassLoader());
        
        try {
            LoggingContext.restore(mdcSnapshot);
            LoggingContext.setTool(toolName);
            
            logger.debug("开始执行");
            
            String fixedArguments = fixJsonArguments(toolName, arguments);
            JsonNode argumentsNode = objectMapper.readTree(fixedArguments);
            String result = toolRegistry.execute(toolName, fixedArguments);
            
            long executionTime = System.currentTimeMillis() - startTime;
            logger.debug("执行完成, duration={}ms", executionTime);
            
            ToolExecutionResult execResult = ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .result(result)
                    .success(true)
                    .executionTimeMs(executionTime)
                    .build();
                    
            if (executor != null && executor.shouldRunInBackground()) {
                notifyToolComplete(toolCall, execResult, index, total);
            }
            return execResult;
                    
        } catch (ToolExecutionException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.debug("执行失败, duration={}ms, error={}", executionTime, e.getMessage());
            ToolExecutionResult execResult = ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
            if (executor != null && executor.shouldRunInBackground()) {
                notifyToolComplete(toolCall, execResult, index, total);
            }
            return execResult;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.debug("执行异常, duration={}ms, error={}", executionTime, e.getMessage());
            ToolExecutionResult execResult = ToolExecutionResult.builder()
                    .index(index)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("参数解析失败: " + e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
            if (executor != null && executor.shouldRunInBackground()) {
                notifyToolComplete(toolCall, execResult, index, total);
            }
            return execResult;
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            LoggingContext.clearTool();
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

    private String fixJsonArguments(String toolName, String arguments) {
        if (!"edit_file".equals(toolName) || arguments == null) {
            return arguments;
        }
        String fixed = arguments;
        fixed = fixFieldValue(fixed, "old_text");
        fixed = fixFieldValue(fixed, "new_text");
        return fixed;
    }
    
    private String fixFieldValue(String json, String fieldName) {
        String pattern1 = "\"" + fieldName + "\":\"";
        String pattern2 = "\"" + fieldName + "\": \"";
        
        int idx1 = json.indexOf(pattern1);
        int idx2 = json.indexOf(pattern2);
        
        int pos;
        if (idx1 >= 0 && idx2 >= 0) {
            pos = Math.min(idx1, idx2) + (idx1 < idx2 ? pattern1.length() : pattern2.length());
        } else if (idx1 >= 0) {
            pos = idx1 + pattern1.length();
        } else if (idx2 >= 0) {
            pos = idx2 + pattern2.length();
        } else {
            return json;
        }
        
        StringBuilder result = new StringBuilder(json.substring(0, pos));
        
        while (pos < json.length()) {
            char c = json.charAt(pos);
            
            if (c == '\\' && pos + 1 < json.length()) {
                char nextChar = json.charAt(pos + 1);
                if (nextChar == '"' || nextChar == '\\' || nextChar == 'n' || 
                    nextChar == 'r' || nextChar == 't') {
                    result.append(c).append(nextChar);
                    pos += 2;
                    continue;
                }
                result.append("\\\\");
                pos++;
                continue;
            }
            
            if (c == '\"') {
                int next = pos + 1;
                while (next < json.length() && Character.isWhitespace(json.charAt(next))) next++;
                if (next < json.length() && (json.charAt(next) == ',' || json.charAt(next) == '}')) {
                    result.append(json.substring(pos));
                    break;
                } else {
                    result.append("\\\"");
                }
            } else if (c == '\n') {
                result.append("\\n");
            } else if (c == '\r') {
                result.append("\\r");
            } else if (c == '\t') {
                result.append("\\t");
            } else {
                result.append(c);
            }
            
            pos++;
        }
        
        return result.toString();
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
