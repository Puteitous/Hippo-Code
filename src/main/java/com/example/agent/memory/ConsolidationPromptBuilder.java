package com.example.agent.memory;

import java.util.List;

/**
 * @deprecated 已迁移到 {@link com.example.agent.memory.consolidation.ConsolidationPromptBuilder}
 * 保留此类仅为向后兼容，新代码应使用 memory.consolidation 包中的版本
 */
@Deprecated
public class ConsolidationPromptBuilder {
    
    private ConsolidationPromptBuilder() {
        // 私有构造函数，防止实例化
    }
    
    public static String buildConsolidationPrompt(String indexText, List<String> unprocessedSessions) {
        return com.example.agent.memory.consolidation.ConsolidationPromptBuilder.buildConsolidationPrompt(indexText, unprocessedSessions);
    }
}
