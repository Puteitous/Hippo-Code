# Simple Java Agent 项目架构文档

## 📋 项目概述

**Simple Java Agent** 是一个基于命令行的智能 AI Agent 应用，提供与 AI 模型交互的终端界面。

| 属性 | 值 |
|------|-----|
| 项目名称 | Simple Java Agent |
| 版本 | 1.0.0 |
| Java 版本 | 21 (启用 Preview 特性) |
| 构建工具 | Maven |
| 打包方式 | Fat Jar (可执行) |

---

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    用户交互层 (CLI)                      │
│                    JLine 终端交互                        │
├─────────────────────────────────────────────────────────┤
│                    业务逻辑层                            │
│              SimpleJavaAgent 主程序                      │
├─────────────────────────────────────────────────────────┤
│                    网络通信层                            │
│         Retrofit + OkHttp + OkHttp-SSE                  │
├─────────────────────────────────────────────────────────┤
│                    数据解析层                            │
│              Jackson JSON 处理                          │
├─────────────────────────────────────────────────────────┤
│                    外部服务层                            │
│              AI API 服务端                              │
└─────────────────────────────────────────────────────────┘
```

---

## 📦 核心依赖

### 网络通信
| 依赖 | 版本 | 用途 |
|------|------|------|
| OkHttp | 4.12.0 | HTTP 客户端，处理网络请求 |
| OkHttp-SSE | 4.12.0 | Server-Sent Events 支持，流式响应 |
| Retrofit | 2.9.0 | REST API 客户端，简化 HTTP 调用 |

### 数据处理
| 依赖 | 版本 | 用途 |
|------|------|------|
| Jackson-Databind | 2.16.1 | JSON 序列化/反序列化 |
| Jackson-Core | 2.16.1 | JSON 核心处理 |
| Jackson-Annotations | 2.16.1 | JSON 注解支持 |

### 用户界面
| 依赖 | 版本 | 用途 |
|------|------|------|
| JLine | 3.25.1 | 命令行交互、输入处理 |
| JLine-Terminal | 3.25.1 | 终端控制 |
| JLine-Reader | 3.25.1 | 输入读取 |

### 测试
| 依赖 | 版本 | 用途 |
|------|------|------|
| JUnit Jupiter | 5.10.2 | 单元测试框架 |

---

## 📁 推荐项目结构

```
simple-java-agent/
├── pom.xml                          # Maven 配置文件
├── ARCHITECTURE.md                  # 架构文档
├── README.md                        # 项目说明
├── test.md                          # 测试/简报文件
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/agent/
    │   │       ├── SimpleJavaAgent.java    # 主程序入口
    │   │       ├── cli/                    # 命令行交互模块
    │   │       │   ├── CommandHandler.java
    │   │       │   └── TerminalUI.java
    │   │       ├── network/                # 网络通信模块
    │   │       │   ├── ApiClient.java
    │   │       │   ├── ApiService.java
    │   │       │   └── SseHandler.java
    │   │       ├── model/                  # 数据模型
    │   │       │   ├── Message.java
    │   │       │   ├── Request.java
    │   │       │   └── Response.java
    │   │       └── util/                   # 工具类
    │   │           ├── JsonUtil.java
    │   │           └── ConfigLoader.java
    │   └── resources/
    │       └── application.properties      # 配置文件
    └── test/
        └── java/
            └── com/example/agent/
                └── AgentTest.java
```

---

## 🔄 核心流程

```
用户输入 → JLine 读取 → 命令解析 → API 请求 → SSE 流式响应 → 终端输出
   │                                              │
   └────────────────── 交互循环 ──────────────────┘
```

### 处理流程说明

1. **启动阶段**
   - 加载配置文件
   - 初始化 Retrofit 客户端
   - 启动 JLine 终端

2. **交互阶段**
   - 读取用户输入
   - 解析命令和参数
   - 构建 API 请求
   - 发送请求并处理流式响应
   - 实时显示响应内容

3. **结束阶段**
   - 处理退出命令
   - 清理资源
   - 优雅关闭

---

## ⚙️ 构建配置

### Maven 插件

| 插件 | 版本 | 功能 |
|------|------|------|
| maven-compiler-plugin | 3.12.1 | 编译配置（Java 21 + Preview） |
| maven-jar-plugin | 3.3.0 | 指定主类 Manifest |
| maven-shade-plugin | 3.5.1 | 打包可执行 Fat Jar |
| maven-surefire-plugin | 3.2.5 | 单元测试执行 |

### 构建命令

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 打包可执行 Jar
mvn package

# 运行程序
java -jar target/simple-java-agent-1.0.0.jar
```

---

## 🔐 配置项

建议在 `application.properties` 中配置以下参数：

```properties
# API 配置
api.base.url=https://api.example.com
api.key=your-api-key
api.timeout=30000

# 终端配置
terminal.prompt=agent>
terminal.history.size=1000
```

---

## 🚀 扩展方向

1. **多模型支持** - 接入不同 AI 模型提供商
2. **插件系统** - 支持命令扩展和功能插件
3. **会话管理** - 支持多轮对话和历史记录
4. **配置文件** - 支持 YAML/JSON 配置格式
5. **日志系统** - 集成 SLF4J + Logback

---

*文档版本：1.0.0 | 最后更新：今日*
