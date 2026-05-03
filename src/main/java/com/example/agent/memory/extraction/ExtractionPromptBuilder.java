package com.example.agent.memory.extraction;

import com.example.agent.llm.model.Message;

import java.util.List;

/**
 * 记忆提取 Prompt 构建器
 * 
 * 设计哲学（参考 Claude Code prompts.ts）：
 * - 触发是强制的（定期创建 SubAgent）
 * - 写入是自主的（LLM 根据指导判断是否值得保存）
 * - 用户明确要求记住时立即保存
 * - 不写入是正常情况，不报错，不重试
 * 
 * Prompt 结构：
 * 1. opener：角色、工具、轮次预算、现有记忆
 * 2. 明确指令：用户要求记住/忘记时立即行动
 * 3. 记忆类型定义（来自 MemoryTypeDefinitions）
 * 4. 排除规则（来自 MemoryTypeDefinitions）
 * 5. 如何保存记忆：两步流程（写文件 + 更新索引）
 */
public class ExtractionPromptBuilder {

    /**
     * 构建完整的提取 Prompt（参考 Claude Code buildExtractAutoOnlyPrompt）
     * 
     * 注意：对话历史通过 API 的 messages 参数传递（由 SubAgentManager 处理）
     * Prompt 中只包含提取指令，不包含对话历史文本，以保证缓存前缀一致
     * 
     * @param newMessages 新消息数量（用于限定分析范围）
     * @param existingMemories 现有记忆文件列表（MEMORY.md 内容）
     */
    public static String buildExtractionPrompt(
            int newMessages,
            String existingMemories) {
        StringBuilder sb = new StringBuilder();
        
        // 1. Opener（角色、工具、轮次预算、现有记忆）
        sb.append(buildOpener(newMessages, existingMemories));
        
        // 2. 明确指令（Claude Code 核心设计）
        sb.append("\n\n如果用户明确要求记住某些内容，立即保存为最合适的记忆类型。");
        sb.append("如果用户要求忘记某些内容，找到并删除相关的记忆条目。");
        
        // 3. 记忆类型定义（参考 Claude Code memoryTypes.ts）
        sb.append("\n\n");
        sb.append(MemoryTypeDefinitions.getTypesSection());
        
        // 4. 排除规则（参考 Claude Code WHAT_NOT_TO_SAVE_SECTION）
        sb.append(MemoryTypeDefinitions.getWhatNotToSaveSection());
        
        // 5. 如何保存记忆（两步流程）
        sb.append("\n");
        sb.append(buildHowToSaveSection());
        
        // 注意：不再包含对话历史，对话历史通过 API messages 参数传递
        sb.append("\n\n---\n\n");
        sb.append("对话历史已在上方提供，请分析最近约 ").append(newMessages).append(" 条消息。");
        
        return sb.toString();
    }

    /**
     * 构建 Opener 部分（参考 Claude Code opener 函数）
     */
    private static String buildOpener(int newMessages, String existingMemories) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你现在作为记忆提取子代理运行。分析最近约 ")
          .append(newMessages)
          .append(" 条消息，用它们来更新你的持久记忆系统。");
        sb.append("\n\n");
        
        sb.append("可用工具：read_file、grep、glob、只读 bash（ls/find/cat/stat/wc/head/tail 等）、以及仅限记忆目录内的 write_file/edit_file。bash rm 不被允许。所有其他工具 — MCP、Agent、可写 bash 等 — 将被拒绝。");
        sb.append("\n\n");
        
        sb.append("你有有限的轮次预算。edit_file 需要先 read_file 同一文件，所以高效的策略是：")
          .append("第 1 轮 — 并行调用 read_file 读取所有可能需要更新的文件；")
          .append("第 2 轮 — 并行调用 write_file/edit_file 写入所有更改。")
          .append("不要跨多轮交替执行读取和写入。");
        sb.append("\n\n");
        
        sb.append("你必须只使用最近约 ")
          .append(newMessages)
          .append(" 条消息中的内容来更新持久记忆。")
          .append("不要浪费任何轮次去尝试调查或验证这些内容 — ")
          .append("不要 grep 源代码、不要读取代码来确认模式是否存在、不要执行 git 命令。");
        
        // 现有记忆列表
        if (existingMemories != null && !existingMemories.isEmpty()) {
            sb.append("\n\n## 现有记忆文件\n\n")
              .append(existingMemories)
              .append("\n\n在写入之前检查这个列表 — 更新现有文件而不是创建重复的记忆。");
        }
        
        return sb.toString();
    }

    /**
     * 构建如何保存记忆部分（参考 Claude Code howToSave）
     */
    private static String buildHowToSaveSection() {
        return """
            
            ## 如何保存记忆
            
            保存记忆是一个两步过程：
            
            **步骤 1** — 将记忆写入独立文件（例如 `user_role.md`、`feedback_testing.md`）
            
            文件路径格式：`.hippo/memory/{type}_{topic}.md`
            
            使用以下 frontmatter 格式：
            ```markdown
            ---
            id: 550e8400-e29b-41d4-a716-446655440000
            type: feedback
            tags: [测试, 数据库]
            ---
            
            # 测试必须使用真实数据库，不用 mock
            
            **Why:** 上个季度 mock 测试通过了但生产迁移失败，团队被坑了。
            
            **How to apply:** 编写集成测试时，使用真实数据库连接，不要 mock 数据库层。
            ```
            
            **步骤 2** — 在 `MEMORY.md` 中添加指向该文件的指针
            
            `MEMORY.md` 是一个索引，不是记忆本身 — 每行一个条目，约 150 字符以内：
            `- [标题](file.md) — one-line hook`
            
            它没有 frontmatter。永远不要将记忆内容直接写入 `MEMORY.md`。
            
            MEMORY.md 示例：
            ```markdown
            - [测试不用 mock 数据库](feedback_no-db-mocks.md) — 曾有 mock/生产差异事故
            - [用户角色：数据科学家](user_data-scientist-role.md) — 用户是数据科学家，关注可观测性
            - [合并冻结 2026-03-05](project_merge-freeze.md) — 移动版本发布
            ```
            
            - `MEMORY.md` 总是被加载到系统提示中 — 超过 200 行将被截断，所以保持索引简洁
            - 按主题语义组织记忆，而不是按时间顺序
            - 更新或删除发现错误或过时的记忆
            - 不要写入重复的记忆。写入新记忆之前先检查是否有可以更新的现有记忆。
            
            """;
    }

    /**
     * 构建简化的提取 Prompt
     */
    public static String buildSimpleExtractionPrompt(int newMessages) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你现在作为记忆提取子代理运行。分析最近约 ")
          .append(newMessages)
          .append(" 条消息，用它们来更新你的持久记忆系统。");
        sb.append("\n\n");
        
        sb.append("可用工具：read_file、grep、glob、只读 bash（ls/find/cat/stat/wc/head/tail 等）、以及仅限记忆目录内的 write_file/edit_file。");
        sb.append("\n\n");
        
        sb.append(MemoryTypeDefinitions.getTypesSection());
        sb.append(MemoryTypeDefinitions.getWhatNotToSaveSection());
        sb.append("\n");
        sb.append(buildHowToSaveSection());
        
        return sb.toString();
    }
}
