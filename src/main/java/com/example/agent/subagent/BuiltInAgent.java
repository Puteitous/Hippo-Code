package com.example.agent.subagent;

public enum BuiltInAgent {
    EXPLORE(
        "explore",
        "代码搜索专家",
        "快速代码探索专家，专门用于搜索代码库、分析项目结构、理解依赖关系、定位具体实现。" +
        "适用于需要大量搜索和代码分析的任务。此专家为**纯只读模式**，不修改任何文件。",
        SubAgentPermission.READ_ONLY,
        "🔍",
        false
    ),
    
    PLAN(
        "plan", 
        "架构设计师",
        "软件架构师，专门用于设计实施方案、分析技术选型、分解复杂任务。" +
        "适用于制定技术方案、规划实施策略、分析架构决策。此专家为**纯只读模式**，专注于高层设计。",
        SubAgentPermission.READ_ONLY,
        "🏗️",
        false
    ),
    
    VERIFICATION(
        "verification",
        "验证专家",
        "独立验证专家。你的工作不是确认实现正确，而是尝试破坏它。" +
        "适用于测试、寻找 Bug、验证正确性、从独立视角验证实现。此专家拥有完整工具权限，但以批判性思维工作。",
        SubAgentPermission.DEFAULT,
        "✅",
        false
    ),
    
    GENERAL(
        "general",
        "通用独立代理",
        "完全独立的通用子代理，从零开始执行任务。适用于完全独立、不需要任何历史上下文的全新任务。" +
        "注意：大多数情况你应该**省略此参数**使用 Fork 优化模式，而不是显式指定 general。",
        SubAgentPermission.DEFAULT,
        "🤖",
        false
    );

    private final String agentType;
    private final String displayName;
    private final String description;
    private final SubAgentPermission permission;
    private final String icon;
    private final boolean useForkOptimization;

    BuiltInAgent(String agentType, 
                 String displayName, 
                 String description, 
                 SubAgentPermission permission, 
                 String icon,
                 boolean useForkOptimization) {
        this.agentType = agentType;
        this.displayName = displayName;
        this.description = description;
        this.permission = permission;
        this.icon = icon;
        this.useForkOptimization = useForkOptimization;
    }

    public String getAgentType() {
        return agentType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public SubAgentPermission getPermission() {
        return permission;
    }

    public String getIcon() {
        return icon;
    }

    public boolean useForkOptimization() {
        return useForkOptimization;
    }

    public static BuiltInAgent fromType(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return null;
        }
        for (BuiltInAgent agent : values()) {
            if (agent.getAgentType().equalsIgnoreCase(agentType)) {
                return agent;
            }
        }
        return null;
    }

    public static String getAgentMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ 【强约束】默认行为\n\n");
        sb.append("### ✅ **推荐：不要指定 subagent_type 参数**\n\n");
        sb.append("不填 subagent_type = **Fork 缓存优化模式**（成本降低 90%）：\n");
        sb.append("- 🚀 完整复用当前上下文（所有对话历史、所有文件状态）\n");
        sb.append("- 💰 **缓存命中率 ~98%，10 个并行任务只花 1.2 倍成本**\n");
        sb.append("- 🧠 子代理知道所有历史，就像你的分身\n");
        sb.append("- ✅ **95% 的任务都应该用这个模式**\n\n");
        
        sb.append("## 📋 仅在以下特殊情况才指定专家类型\n\n");
        for (BuiltInAgent agent : values()) {
            sb.append("- `").append(agent.getAgentType()).append("`: ")
              .append(agent.getIcon()).append(" **").append(agent.getDisplayName()).append("**")
              .append(" - ").append(agent.getDescription()).append("\n");
        }
        
        sb.append("\n## 🎯 判断标准\n\n");
        sb.append("| 条件 | 做法 |\n");
        sb.append("|------|------|\n");
        sb.append("| ✅ 任务需要知道上下文历史 | **省略 subagent_type** |\n");
        sb.append("| ✅ 任务和当前工作相关 | **省略 subagent_type** |\n");
        sb.append("| ❌ 任务完全独立，不需要历史 | `type=\"general\"` |\n");
        sb.append("| ❌ 只做代码搜索分析 | `type=\"explore\"` |\n");
        sb.append("| ❌ 只做方案设计 | `type=\"plan\"` |\n");
        sb.append("| ❌ 需要独立第三方验证 | `type=\"verification\"` |\n\n");
        
        sb.append("## 💡 软引导\n\n");
        sb.append("- 当你不确定时 → **省略 subagent_type**\n");
        sb.append("- 当你想省钱时 → **省略 subagent_type**\n");
        sb.append("- 当任务需要上下文 → **省略 subagent_type**\n");
        sb.append("- 只有 100% 确定需要专家角色才指定类型\n");
        
        return sb.toString();
    }

    public String getSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(getIcon()).append(" ").append(getDisplayName()).append("\n\n");
        sb.append("## 🎯 角色定位\n");
        sb.append(getDescription()).append("\n\n");
        sb.append("## 🔧 工具权限\n");
        sb.append("可用工具: ").append(getPermission().getAllowedTools()).append("\n\n");
        sb.append("## ⚠️ 强制执行原则\n");
        if (getPermission() == SubAgentPermission.READ_ONLY) {
            sb.append("- ✅ **纯只读模式**：专注于分析和理解，不修改任何文件\n");
        }
        sb.append("- ✅ **必须调用工具获取真实数据** - 严禁编造任何结果\n");
        sb.append("- ✅ 信息不足就调用更多工具 - 绝不猜测、绝不假设\n");
        sb.append("- ❌ **严禁询问用户** - 自主执行任务，不追问任何问题\n");
        sb.append("- ✅ 结果明确立即输出总结 - 不要无意义循环\n");
        sb.append("- ❌ 严禁无意义重复调用同样参数的工具\n");
        sb.append("- ✅ 如实汇报结果 - 工具返回什么就总结什么\n");
        return sb.toString();
    }
}
