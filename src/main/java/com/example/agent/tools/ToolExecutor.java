package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public interface ToolExecutor {
    
    String getName();
    
    String getDescription();
    
    String getParametersSchema();
    
    String execute(JsonNode arguments) throws ToolExecutionException;

    default String execute(JsonNode arguments, Consumer<String> progressCallback)
            throws ToolExecutionException {
        return execute(arguments);
    }

    default List<String> getAffectedPaths(JsonNode arguments) {
        return Collections.emptyList();
    }

    /**
     * 从工具参数中提取被操作的文件路径列表。
     * 默认实现委托给 getAffectedPaths，子类可覆盖以提供更精确的逻辑。
     * 用于回滚系统定位变更记录。
     */
    default List<String> getFilePaths(JsonNode arguments) {
        return getAffectedPaths(arguments);
    }

    default boolean requiresFileLock() {
        return false;
    }

    default boolean shouldRunInBackground() {
        return true;
    }
}
