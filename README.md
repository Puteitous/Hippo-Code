# Hippo Code - 智能 AI 编程助手

## 📋 项目概述

**Hippo Code** 是一个基于命令行的智能 AI Agent 应用，提供与 AI 模型交互的终端界面，支持代码编辑、文件操作、意图识别、任务规划等高级功能。

| 属性 | 值 |
|------|-----|
| 项目名称 | Hippo Code |
| 版本 | 1.0.0 |
| Java 版本 | 21 (启用 Preview 特性) |
| 构建工具 | Maven |
| 打包方式 | Fat Jar (可执行) |

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      用户交互层 (CLI)                            │
│              AgentUi / InputHandler / CommandDispatcher         │
├─────────────────────────────────────────────────────────────────┤
│                      执行控制层                                  │
│         ConversationLoop / AgentTurnExecutor                    │
├─────────────────────────────────────────────────────────────────┤
│                      智能规划层                                  │
│    IntentRecognizer / TaskPlanner / PlanExecutor                │
├─────────────────────────────────────────────────────────────────┤
│                      上下文管理层                                │
│  ConversationManager / SlidingWindowPolicy / TokenEstimator     │
├─────────────────────────────────────────────────────────────────┤
│                      工具执行层                                  │
│      ToolRegistry / ConcurrentToolExecutor / 8+ Tools           │
├─────────────────────────────────────────────────────────────────┤
│                      LLM 通信层                                  │
│         DefaultLlmClient / SSE Stream / Retry Policy            │
├─────────────────────────────────────────────────────────────────┤
│                      基础设施层                                  │
│    SessionStorage / ConversationLogger / TokenMetricsCollector  │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✨ 核心功能

### 🧠 智能对话
- **意图识别**：基于规则和 LLM 的混合意图识别，自动理解用户需求
- **任务规划**：将复杂任务分解为可执行步骤，支持顺序/并行执行策略
- **上下文管理**：滑动窗口策略自动管理对话历史，Token 计数精确控制
- **会话持久化**：支持会话保存、恢复、自动清理过期会话

### 🛠️ 工具集
| 工具 | 功能 |
|------|------|
| `read_file` | 读取文件内容 |
| `write_file` | 写入文件（覆盖整个文件）|
| `edit_file` | 精确编辑文件（替换特定文本片段）|
| `list_directory` | 列出目录内容，支持递归显示 |
| `glob` | 使用 glob 模式查找文件 |
| `grep` | 在文件内容中搜索（支持正则）|
| `bash` | 执行终端命令（白名单安全控制）|
| `ask_user` | 向用户提问并等待回答 |

### 📊 监控与日志
- **对话日志**：完整记录每轮对话，支持 JSON 格式导出
- **Token 统计**：实时统计输入/输出 Token 使用量
- **Tiktoken 支持**：精确的 Token 计数（兼容 GPT-4 编码）

---

## 📦 核心依赖

### 网络通信
| 依赖 | 版本 | 用途 |
|------|------|------|
| OkHttp | 4.12.0 | HTTP 客户端，处理网络请求 |
| OkHttp-SSE | 4.12.0 | Server-Sent Events 支持，流式响应 |

### 数据处理
| 依赖 | 版本 | 用途 |
|------|------|------|
| Jackson | 2.16.1 | JSON 序列化/反序列化 |
| SnakeYAML | 2.2 | YAML 配置文件解析 |

### 用户界面
| 依赖 | 版本 | 用途 |
|------|------|------|
| JLine | 3.25.1 | 命令行交互、终端控制 |

### 工具库
| 依赖 | 版本 | 用途 |
|------|------|------|
| Tiktoken | 0.9.0 | OpenAI Token 计数 |
| SLF4J + Logback | 1.4.14 + 1.4.14 | 日志框架 |

### 测试
| 依赖 | 版本 | 用途 |
|------|------|------|
| JUnit Jupiter | 5.10.2 | 单元测试框架 |
| Mockito | 5.10.0 | Mock 测试框架 |

---

## 📁 项目结构

```
hippo-code/
├── pom.xml                              # Maven 配置
├── config.yaml.example                   # 配置文件示例
├── ARCHITECTURE.md                       # 架构文档
├── README.md                             # 项目说明
└── src/
    ├── main/
    │   ├── java/com/example/agent/
    │   │   ├── AgentApplication.java         # 应用入口
    │   │   ├── SimpleJavaAgent.java          # Agent 主程序
n    │   │   ├── core/                         # 核心模块
    │   │   │   └── AgentContext.java          # Agent 上下文
    │   │   ├── config/                       # 配置模块
    │   │   │   ├── Config.java               # 全局配置
    │   │   │   ├── LlmConfig.java            # LLM 配置
    │   │   │   ├── ContextConfig.java        # 上下文配置
    │   │   │   ├── IntentConfig.java         # 意图识别配置
    │   │   │   └── SessionConfig.java        # 会话配置
    │   │   ├── console/                      # 控制台模块
    │   │   │   ├── AgentUi.java              # UI 渲染
    │   │   │   ├── InputHandler.java         # 输入处理
    │   │   │   └── CommandDispatcher.java    # 命令分发
    │   │   ├── execute/                      # 执行模块
    │   │   │   ├── ConversationLoop.java     # 对话循环
    │   │   │   ├── AgentTurnExecutor.java    # 单轮执行器
    │   │   │   └── ToolCallProcessor.java     # 工具调用处理
    │   │   ├── intent/                       # 意图识别
    │   │   │   ├── IntentRecognizer.java     # 识别接口
    │   │   │   ├── RuleBasedIntentRecognizer.java  # 规则识别
    │   │   │   ├── LlmIntentRecognizer.java  # LLM 识别
    │   │   │   └── HybridIntentRecognizer.java    # 混合识别
    │   │   ├── plan/                         # 任务规划
    │   │   │   ├── TaskPlanner.java          # 规划接口
    │   │   │   ├── SimpleTaskPlanner.java    # 简单规划
    │   │   │   ├── LlmTaskPlanner.java       # LLM 规划
    │   │   │   └── SequentialPlanExecutor.java    # 顺序执行
    │   │   ├── llm/                          # LLM 通信
    │   │   │   ├── client/                   # 客户端
    │   │   │   ├── model/                    # 数据模型
    │   │   │   ├── stream/                   # SSE 流处理
    │   │   │   ├── retry/                    # 重试策略
    │   │   │   └── exception/                # 异常定义
    │   │   ├── context/                      # 上下文管理
    │   │   │   ├── policy/                   # 裁剪策略
    │   │   │   ├── compressor/               # 压缩器
    │   │   │   └── config/                   # 配置
    │   │   ├── service/                      # 服务层
    │   │   │   ├── ConversationManager.java  # 对话管理
    │   │   │   ├── TokenEstimator.java       # Token 估算
    │   │   │   └── TiktokenEstimator.java    # Tiktoken 实现
    │   │   ├── session/                      # 会话管理
    │   │   │   ├── SessionData.java          # 会话数据
    │   │   │   └── SessionStorage.java       # 会话存储
    │   │   ├── tools/                        # 工具集
    │   │   │   ├── ToolRegistry.java         # 工具注册
    │   │   │   ├── ReadFileTool.java         # 读文件
    │   │   │   ├── WriteFileTool.java        # 写文件
    │   │   │   ├── EditFileTool.java         # 编辑文件
    │   │   │   ├── GlobTool.java             # 文件查找
    │   │   │   ├── GrepTool.java             # 内容搜索
    │   │   │   ├── BashTool.java             # 命令执行
    │   │   │   └── concurrent/               # 并发执行
    │   │   └── logging/                      # 日志模块
    │   │       ├── ConversationLogger.java  # 对话日志
    │   │       └── TokenMetricsCollector.java    # Token 统计
    │   └── resources/
    │       └── logback.xml                  # 日志配置
    └── test/java/com/example/agent/         # 测试代码
```

---

## 🔄 核心流程

```
用户输入
   │
   ▼
┌─────────────────┐
│  InputHandler   │ ← 处理长输入、命令解析
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ IntentRecognizer│ ← 意图识别（规则 + LLM）
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  TaskPlanner    │ ← 任务规划、步骤分解
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ConversationLoop │ ← 对话循环控制
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│AgentTurnExecutor│ ← 单轮执行
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│  LLM  │ │ Tools │ ← 工具调用
└───┬───┘ └───┬───┘
    │         │
    └────┬────┘
         ▼
┌─────────────────┐
│ ContextManager  │ ← 上下文管理、历史裁剪
└────────┬────────┘
         │
         ▼
    终端输出/会话保存
```

### 处理流程说明

1. **启动阶段**
   - 加载 `config.yaml` 配置文件
   - 初始化 LLM 客户端、工具注册表、Token 估算器
   - 检查可恢复的会话
   - 启动 JLine 终端

2. **交互阶段**
   - 读取用户输入，处理特殊命令
   - 意图识别：判断任务类型
   - 任务规划：分解复杂任务
   - 构建 API 请求，发送到 LLM
   - 处理 SSE 流式响应，实时显示
   - 执行工具调用，返回结果
   - 自动管理上下文窗口

3. **结束阶段**
   - 保存会话状态
   - 记录 Token 使用统计
   - 清理资源，优雅关闭

---

## ⚙️ 构建配置

### 构建命令

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 打包可执行 Jar
mvn package

# 运行程序
java -jar target/hippo-code-1.0.0.jar
```

---

## 🔐 配置说明

配置文件为 `config.yaml`，参考 `config.yaml.example` 创建：

```yaml
# LLM 配置
llm:
  provider: dashscope           # 提供商
  api_key: ${DASHSCOPE_API_KEY} # 环境变量
  model: qwen-plus              # 模型名称
  base_url: https://dashscope.aliyuncs.com
  max_tokens: 2048
  temperature: 0.7
  timeout: 60000

# 上下文管理
context:
  max_tokens: 30000             # 最大 Token 数
  max_messages: 20              # 最大消息数
  keep_recent_turns: 6          # 保留最近轮次
  tool_result:
    max_tokens: 2000            # 工具结果最大 Token
    truncate_strategy: tail     # 截断策略

# 意图识别
intent:
  enabled: true
  recognition:
    mode: hybrid                # hybrid | rule | llm
    llm_enabled: true
    high_confidence_threshold: 0.85

# 任务规划
planning:
  mode: composite               # composite | simple | llm
  enable_complex_intent_detection: true

# 会话管理
session:
  persist_sessions: true        # 持久化会话
  max_saved_sessions: 10        # 最大保存数
  auto_resume: true             # 自动恢复
  resume_timeout_hours: 72      # 超时时间

# Token 估算
tokenizer:
  type: tiktoken                # tiktoken | simple
  model: gpt-4
  cache_enabled: true

# 工具配置
tools:
  bash:
    enabled: true
    whitelist: [git, mvn, npm, docker, ls]
    require_confirmation: true
  file:
    enabled: true
    allowed_paths: ["."]
    max_file_size: 10MB
```


---

## 📝 命令列表

| 命令 | 功能 |
|------|------|
| `help` | 显示帮助信息 |
| `exit` / `quit` | 退出程序 |
| `clear` | 清屏 |
| `reset` | 重置会话 |
| `retry` | 重试上次请求 |
| `config` | 显示当前配置 |
| `showlog` | 显示日志文件路径 |
| `tokens` | 显示 Token 使用统计 |

---

## 📄 许可证

MIT License

---

*文档版本：1.0.0 | 最后更新：2026-04*
