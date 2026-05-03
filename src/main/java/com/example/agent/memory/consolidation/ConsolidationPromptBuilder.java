package com.example.agent.memory.consolidation;

import java.util.List;

/**
 * 记忆整合 Prompt 构建器
 * 
 * 职责：
 * 1. 构建四阶段记忆整合 Prompt
 * 2. 包含 Orient → Gather → Consolidate → Prune 流程
 */
public class ConsolidationPromptBuilder {

    private static final String DREAM_PROMPT_TEMPLATE = """
        # Dream: Memory Consolidation

        你是 Hippo Code 的记忆整合专家。你的任务是将单会话的、过程性的 Session Memory
        提炼为跨会话的、结构化的长期记忆。

        ## 🎯 核心原则
        - **自主性**：你自主完成整个整合周期，无需人工干预
        - **安全性**：所有写操作必须在 MemoryToolSandbox 内完成
        - **可靠性**：即使部分步骤失败，状态仍保持一致
        - **质量优先**：只保留真正有价值的洞察，不要记录临时调试信息

        ---

        ## Phase 1 — Orient

        读取 Hippo 长期记忆索引，了解现有知识库。

        **现有记忆索引**：
        %s

        输出：现有记忆的主题列表（用于后续去重和冲突检测）

        ---

        ## Phase 2 — Gather

        读取项目 logs/ 目录和 grep 关键 transcripts，提取有价值的信息。

        **未处理会话记忆**（共 %d 个）：
        %s

        ⚠️ **L3 真相层使用规则**：
        - **0 次 grep 是完美流程**——只有在发现矛盾、模糊或极度重要的线索时才使用
        - 每次最多调用 3 次 grep（这是上限，不是目标）
        - 不要试图重读整个 transcript
        - 如果 L1/L2 信息足够清晰，完全不需要使用 L3

        输出：新信息摘要（按主题分类）

        ---

        ## Phase 3 — Consolidate

        基于新信息，决定以下操作：

        - **CREATE**：使用 `MemoryStore.add()` 创建全新的记忆
        - **UPDATE**：使用 `MemoryStore.update()` 更新现有记忆
        - **MERGE**：如果新信息与多个旧条目重叠，合并它们并删除过时记录
        - **DISCARD**：如果是噪音或临时状态，不做处理

        **决策指南（基于语义匹配，非数字打分）**：
        - 发现跨会话有价值的信息 → CREATE
        - 与现有记忆相关但提供新视角 → UPDATE
        - 多个文件讨论同一主题 → MERGE
        - 临时调试信息、已解决的问题 → DISCARD

        输出：操作日志（每个操作一行）

        ---

        ## Phase 4 — Prune

        审查最终记忆索引状态。

        - 检查是否有重复或过时的记忆
        - 确保 MEMORY.md 反映最新变化
        - 验证所有操作的完整性

        输出：最终统计（CREATE: X, UPDATE: Y, MERGE: Z, DISCARD: W）

        ---

        ⚡ **立即开始执行 Phase 1**
        """;

    /**
     * 构建完整的四阶段 Prompt
     *
     * @param indexText 现有记忆索引文本（L2 信息源）
     * @param unprocessedSessions 未处理会话记忆内容列表（L1 信息源）
     * @return 完整的 Prompt 文本
     */
    public static String buildConsolidationPrompt(String indexText, List<String> unprocessedSessions) {
        String sessionsContent = String.join("\n\n---\n\n", unprocessedSessions);
        
        return DREAM_PROMPT_TEMPLATE.formatted(
            indexText != null ? indexText : "（无现有记忆）",
            unprocessedSessions.size(),
            sessionsContent
        );
    }
}
