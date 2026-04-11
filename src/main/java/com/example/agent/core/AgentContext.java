package com.example.agent.core;

import com.example.agent.config.Config;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.domain.rule.RuleManager;
import com.example.agent.domain.cache.CacheManager;
import com.example.agent.domain.index.CodeIndex;
import com.example.agent.service.FileContentService;
import com.example.agent.llm.client.DefaultLlmClient;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.logging.LogDirectoryManager;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.plan.LlmTaskPlanner;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.tools.*;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

public class AgentContext {

    private static final Logger logger = LoggerFactory.getLogger(AgentContext.class);

    public static final String SYSTEM_PROMPT = """
        你是一个编程助手，可以帮助用户进行软件开发任务。
        
        你可以访问以下工具：
        - read_file: 读取文件内容（支持缓存和智能截断）
        - write_file: 写入文件内容（覆盖整个文件）
        - edit_file: 精确编辑文件内容（替换特定文本片段）
        - list_directory: 列出目录内容，支持递归显示目录树
        - glob: 使用 glob 模式查找文件（如 **/*.java 查找所有 Java 文件）
        - grep: 在文件内容中搜索文本（支持正则表达式）
        - search_code: 语义检索代码库，查找相关代码文件
        - ask_user: 向用户提问并等待回答（用于确认或获取信息）
        - bash: 执行终端命令（如 git, mvn, npm 等，有安全限制）
        
        === 自主决策原则 ===
        
        🔍 上下文自主发现：
        - 不要等待用户告诉你"读哪个文件"，你应该主动判断需要哪些信息
        - 如果你对代码库不了解，先用 list_directory、glob、grep 探索项目结构
        - 如果回答问题需要上下文，主动调用 read_file 读取相关文件
        - 可以多次调用工具获取信息，直到你有足够的上下文回答问题
        
        📌 @引用语法糖支持：
        - 用户输入中的 @path/to/file 表示"引用这个文件"
        - 看到 @path/to/file 时，你应该主动调用 read_file 读取该文件
        - 例如："请重构 @src/main/Example.java" → 你需要先读取 Example.java 再回答
        - 支持相对路径和绝对路径
        
        🎯 工具调用策略：
        - 先探索，后回答：处理复杂任务时，先用工具了解项目
        - 按需调用：缺少什么信息，就调用什么工具获取
        - 多次迭代：可以分多次调用工具，逐步深入
        - 用户不需要知道你调用了哪些工具，他们只关心最终答案
        
        当用户请求涉及文件操作时，请使用相应的工具完成任务。
        在修改文件之前，请先读取文件内容了解当前状态。
        优先使用 edit_file 进行精确修改，只有在创建新文件或需要完全重写时才使用 write_file。
        在处理项目相关任务时，先用 list_directory 了解项目结构，用 glob 快速定位文件，用 grep 搜索代码内容。
        查找隐藏文件（如 .gitignore）时，使用简单文件名模式（如 *.gitignore 或直接使用文件名）。
        当遇到不确定的情况或需要用户确认时，使用 ask_user 向用户提问。
        需要执行构建、测试、版本控制等操作时，使用 bash 工具（注意：只允许白名单内的命令）。
        完成任务后，请简要说明你做了什么。
        
        请始终使用中文回复。
        """;

    private final Config config;
    private final Terminal terminal;
    private final LineReader reader;
    
    private LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private ConcurrentToolExecutor concurrentToolExecutor;
    private TokenEstimator tokenEstimator;
    private ConversationManager conversationManager;
    private TokenMetricsCollector tokenMetricsCollector;
    private RuleManager ruleManager;
    private CacheManager cacheManager;
    private FileContentService fileContentService;
    private CodeIndex codeIndex;
    private ThinkingEngine thinkingEngine;

    public AgentContext() throws IOException {
        this.config = Config.getInstance();
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter("help", "exit", "quit", "clear", "reset", "retry", "config", "showlog", "tokens"))
                .variable(LineReader.HISTORY_FILE, java.nio.file.Paths.get(".agent_history"))
                .build();
    }

    public void initialize() {
        LogDirectoryManager.ensureDirectoriesExist();
        this.tokenMetricsCollector = new TokenMetricsCollector(LocalDate.now());
        logger.info("日志系统已初始化");
        
        this.llmClient = new DefaultLlmClient(config);
        this.tokenEstimator = TokenEstimatorFactory.create(config);
        
        // 初始化 RuleManager - 加载规则文件
        this.ruleManager = new RuleManager(tokenEstimator, config.getContext().getHotMemory());
        this.ruleManager.loadHippoRules();
        this.ruleManager.loadMemoryMd();
        logger.info("RuleManager 初始化完成");
        
        // 初始化 CacheManager - 通用缓存
        this.cacheManager = new CacheManager();
        
        // 初始化 FileContentService - 文件服务（带缓存和智能截断）
        this.fileContentService = new FileContentService(tokenEstimator, cacheManager);
        
        // 初始化 CodeIndex - 代码检索引擎
        this.codeIndex = new CodeIndex(tokenEstimator, config.getContext().getColdMemory());
        this.codeIndex.buildIndex();
        logger.info("代码索引构建完成");
        
        // 创建工具注册表并注入依赖
        this.toolRegistry = createToolRegistry();
        
        // 注入 FileContentService 到 ReadFileTool
        ToolExecutor readFileTool = toolRegistry.getExecutor("read_file");
        if (readFileTool instanceof ReadFileTool) {
            ((ReadFileTool) readFileTool).setFileContentService(this.fileContentService);
        }
        
        // 注入 CodeIndex 到 SearchCodeTool
        ToolExecutor searchCodeTool = toolRegistry.getExecutor("search_code");
        if (searchCodeTool instanceof SearchCodeTool) {
            ((SearchCodeTool) searchCodeTool).setCodeIndex(this.codeIndex);
        }
        
        this.concurrentToolExecutor = new ConcurrentToolExecutor(toolRegistry);

        // Phase 1: 初始化 ThinkingEngine - 统一思考引擎
        this.thinkingEngine = new ThinkingEngine(
                this.llmClient,
                this.toolRegistry,
                this.concurrentToolExecutor
        );

        logger.info("统一思考引擎 ThinkingEngine 初始化完成 ✅");
        
        // 增强系统提示词
        String enhancedSystemPrompt = this.ruleManager.enhanceSystemPrompt(SYSTEM_PROMPT);
        this.conversationManager = new ConversationManager(enhancedSystemPrompt, tokenEstimator, config.getContext());
    }

    private ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new ListDirectoryTool());
        registry.register(new GlobTool());
        registry.register(new GrepTool());
        registry.register(new SearchCodeTool());
        registry.register(new EditFileTool());
        registry.register(new AskUserTool());
        registry.register(new BashTool());
        return registry;
    }

    public void resetConversation() {
        // 重新加载规则（确保文件有更新时能重新加载）
        if (this.ruleManager != null) {
            this.ruleManager.reload();
            String enhancedSystemPrompt = this.ruleManager.enhanceSystemPrompt(SYSTEM_PROMPT);
            this.conversationManager = new ConversationManager(enhancedSystemPrompt, tokenEstimator, config.getContext());
        } else {
            this.conversationManager = new ConversationManager(SYSTEM_PROMPT, tokenEstimator, config.getContext());
        }
    }

    public Config getConfig() {
        return config;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public LineReader getReader() {
        return reader;
    }

    public LlmClient getLlmClient() {
        return llmClient;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ConcurrentToolExecutor getConcurrentToolExecutor() {
        return concurrentToolExecutor;
    }

    public TokenEstimator getTokenEstimator() {
        return tokenEstimator;
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public TokenMetricsCollector getTokenMetricsCollector() {
        return tokenMetricsCollector;
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public FileContentService getFileContentService() {
        return fileContentService;
    }

    public CodeIndex getCodeIndex() {
        return codeIndex;
    }

    public ThinkingEngine getThinkingEngine() {
        return thinkingEngine;
    }

    public void close() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
            logger.error("关闭终端失败", e);
        }
    }
}
