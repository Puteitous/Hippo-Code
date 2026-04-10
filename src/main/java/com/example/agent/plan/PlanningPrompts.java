package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;

import java.util.HashMap;
import java.util.Map;

public class PlanningPrompts {

    private static final String SYSTEM_PROMPT = """
            你是一个任务规划专家。你的职责是根据用户的意图和上下文，生成一个结构化的执行计划。
            
            === 重要：规划前先了解真实项目结构 ===
            
            在输出最终计划前，你可以调用工具了解真实代码库结构：
            - glob: 查找特定类型的文件（如 **/*Config.java）
            - grep: 在代码中搜索关键词
            - list_directory: 查看目录结构
            - read_file: 读取关键文件内容
            
            规划原则：
            1. ❌ 禁止猜测文件路径、类名、方法名！不确定就调用工具确认
            2. ✅ 基于真实代码结构输出计划，越具体越好
            3. 步骤应该具体到文件路径和修改位置
            4. 合理设置步骤依赖关系
            
            充分了解项目后，返回一个JSON格式的执行计划，包含以下字段：
            - strategy: 执行策略，可选值为 "SEQUENTIAL"、"PARALLEL"、"CONDITIONAL"、"ADAPTIVE"
            - steps: 步骤数组，每个步骤包含：
              - id: 步骤唯一标识，格式如 "step-1", "step-2"
              - type: 步骤类型
              - description: 步骤描述
              - toolName: 工具名称（仅当type为TOOL_CALL时）
              - arguments: 参数对象（可选）
              - dependencies: 依赖的步骤ID数组（可选）
            
            只返回JSON，不要包含其他解释。
            """;

    private static final String LEGACY_SYSTEM_PROMPT = """
            你是一个任务规划专家。你的职责是根据用户的意图和上下文，生成一个结构化的执行计划。
            
            你需要返回一个JSON格式的执行计划，包含以下字段：
            - strategy: 执行策略，可选值为 "SEQUENTIAL"（顺序执行）、"PARALLEL"（并行执行）、"CONDITIONAL"（条件执行）、"ADAPTIVE"（自适应执行）
            - steps: 步骤数组，每个步骤包含：
              - id: 步骤唯一标识，格式如 "step-1", "step-2"
              - type: 步骤类型，可选值为 "LLM_CALL"（LLM调用）、"TOOL_CALL"（工具调用）、"FILE_READ"（文件读取）、"FILE_WRITE"（文件写入）、"CONDITION"（条件判断）、"PARALLEL"（并行执行）
              - description: 步骤描述
              - toolName: 工具名称（仅当type为TOOL_CALL时需要）
              - arguments: 参数对象（可选）
              - dependencies: 依赖的步骤ID数组（可选）
            
            规划原则：
            1. 步骤应该具体、可执行
            2. 合理设置步骤依赖关系
            3. 对于复杂任务，考虑分解为多个步骤
            4. 选择合适的执行策略
            
            只返回JSON，不要包含其他解释。
            """;

    private static final Map<IntentType, String> INTENT_HINTS = new HashMap<>();

    static {
        INTENT_HINTS.put(IntentType.CODE_GENERATION, """
                代码生成任务通常需要：
                1. 理解用户需求
                2. 生成代码
                3. 可能需要写入文件
                """);

        INTENT_HINTS.put(IntentType.CODE_MODIFICATION, """
                代码修改任务通常需要：
                1. 读取目标文件
                2. 分析现有代码
                3. 进行修改
                4. 写入修改后的代码
                """);

        INTENT_HINTS.put(IntentType.DEBUGGING, """
                调试任务通常需要：
                1. 分析错误信息
                2. 查找相关代码
                3. 定位问题
                4. 提供解决方案
                """);

        INTENT_HINTS.put(IntentType.FILE_OPERATION, """
                文件操作任务通常需要：
                1. 确定操作类型（读取/写入/删除等）
                2. 定位目标文件
                3. 执行操作
                """);

        INTENT_HINTS.put(IntentType.PROJECT_ANALYSIS, """
                项目分析任务通常需要：
                1. 扫描项目结构
                2. 分析关键文件
                3. 生成分析报告
                """);

        INTENT_HINTS.put(IntentType.CODE_REVIEW, """
                代码审查任务通常需要：
                1. 读取目标代码
                2. 分析代码质量
                3. 提供改进建议
                """);
    }

    public static String getSystemPrompt() {
        return LEGACY_SYSTEM_PROMPT;
    }

    public static String getEnhancedSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static boolean isThinkingEngineEnabled() {
        return true;
    }

    public static String getIntentHint(IntentType type) {
        return INTENT_HINTS.getOrDefault(type, "");
    }

    public static String buildPlanningPrompt(IntentResult intent, PlanningContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("用户输入：").append(context.getUserInput()).append("\n\n");

        prompt.append("识别的意图：").append(intent.getType().getDisplayName());
        if (intent.getReasoning() != null && !intent.getReasoning().isEmpty()) {
            prompt.append("（").append(intent.getReasoning()).append("）");
        }
        prompt.append("\n\n");

        String hint = getIntentHint(intent.getType());
        if (!hint.isEmpty()) {
            prompt.append("任务提示：\n").append(hint).append("\n");
        }

        if (!intent.getEntities().isEmpty()) {
            prompt.append("识别的实体：\n");
            intent.getEntities().forEach((key, value) -> {
                prompt.append("- ").append(key).append(": ").append(value).append("\n");
            });
            prompt.append("\n");
        }

        prompt.append("请生成执行计划（JSON格式）：");

        return prompt.toString();
    }
}
