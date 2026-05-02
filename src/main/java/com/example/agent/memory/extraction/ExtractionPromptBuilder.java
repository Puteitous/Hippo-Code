package com.example.agent.memory.extraction;

import com.example.agent.llm.model.Message;

import java.util.List;

/**
 * 记忆提取 Prompt 构建器
 * 
 * 职责：
 * 1. 构建长期记忆提取的 Prompt
 * 2. 包含提取原则、记忆类型、写入规则
 */
public class ExtractionPromptBuilder {

    private static final String BASE_PROMPT = """
        # 长期记忆提取任务

        你是 Hippo Code 的长期记忆提取专家。你的任务是从对话中提取有价值的信息，
        写入到长期记忆系统中，以便在未来的会话中使用。

        ## 🎯 核心原则
        - **自主性**：你自主完成整个提取周期，无需人工干预
        - **安全性**：所有写操作必须在 MemoryToolSandbox 内完成
        - **可靠性**：即使部分步骤失败，状态仍保持一致
        - **质量优先**：只保留真正有价值的洞察，不要记录临时调试信息

        ---

        ## Phase 1 — Analyze

        分析对话历史，识别有价值的信息。

        **关注点**：
        - 用户偏好：代码风格、工具偏好、沟通偏好
        - 项目约束：架构决策、技术栈、关键配置
        - 反馈：用户对代码的反馈、修正建议
        - 经验教训：有效/无效的方法、避坑指南

        输出：候选记忆列表（每条包含类型、重要性、置信度）

        ---

        ## Phase 2 — Filter

        过滤候选记忆，只保留高质量的。

        **过滤标准**：
        - 重要性 ≥ 0.7
        - 置信度 ≥ 0.8
        - 跨会话有价值（不是临时状态）

        输出：过滤后的记忆列表

        ---

        ## Phase 3 — Write

        使用 MemoryStore API 写入记忆。

        **写入规则**：
        - 每条记忆一个文件（UUID.md）
        - 包含 frontmatter 元数据
        - 内容简洁精准

        输出：写入操作日志

        ---

        ## Phase 4 — Verify

        验证写入的记忆。

        - 检查记忆文件是否存在
        - 验证 frontmatter 元数据
        - 确保内容质量

        输出：验证报告

        ---

        ⚡ **立即开始执行 Phase 1**
        """;

    /**
     * 构建完整的提取 Prompt
     *
     * @param conversation 对话历史
     * @param memoryCount 当前记忆数量
     * @return 完整的 Prompt 文本
     */
    public static String buildExtractionPrompt(List<Message> conversation, int memoryCount) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(BASE_PROMPT);
        sb.append("\n\n---\n\n");
        sb.append("## 📊 当前状态\n\n");
        sb.append("- 对话消息数: ").append(conversation.size()).append("\n");
        sb.append("- 当前记忆数: ").append(memoryCount).append("\n");
        sb.append("\n---\n\n");
        sb.append("请立即开始执行提取任务。\n");
        
        return sb.toString();
    }

    /**
     * 构建简化的提取 Prompt（用于快速提取）
     *
     * @param conversation 对话历史
     * @return 简化的 Prompt 文本
     */
    public static String buildSimpleExtractionPrompt(List<Message> conversation) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# 长期记忆提取任务\n\n");
        sb.append("请从以下对话中提取有价值的长期记忆。\n\n");
        sb.append("## 提取原则\n");
        sb.append("- 只提取跨会话有价值的信息\n");
        sb.append("- 用户偏好、项目约束、重要决策、经验教训\n");
        sb.append("- 不要提取临时调试信息或一次性操作\n\n");
        sb.append("## 记忆类型\n");
        sb.append("1. **用户偏好** (user_preference)\n");
        sb.append("2. **项目约束** (project_context)\n");
        sb.append("3. **反馈** (feedback)\n");
        sb.append("4. **参考资料** (reference)\n\n");
        sb.append("## 写入规则\n");
        sb.append("- 使用 MemoryStore API 进行所有写操作\n");
        sb.append("- 每条记忆一个文件（UUID.md）\n");
        sb.append("- 包含 frontmatter 元数据\n\n");
        sb.append("## 对话历史\n\n");
        sb.append(formatConversation(conversation));
        sb.append("\n\n---\n\n");
        sb.append("请立即开始执行提取任务。\n");
        
        return sb.toString();
    }

    /**
     * 格式化对话历史
     */
    private static String formatConversation(List<Message> conversation) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conversation.size(); i++) {
            Message msg = conversation.get(i);
            sb.append("### 消息 ").append(i + 1).append(" [").append(msg.getRole()).append("]\n\n");
            sb.append(truncate(msg.getContent(), 500)).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 截断文本
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [已截断]";
    }
}
