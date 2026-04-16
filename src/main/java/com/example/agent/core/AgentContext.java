package com.example.agent.core;

import com.example.agent.config.Config;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.core.di.CoreModule;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.domain.rule.RuleManager;
import com.example.agent.domain.cache.CacheManager;
import com.example.agent.domain.index.CodeIndex;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.logging.EventMetricsCollector;
import com.example.agent.logging.LogDirectoryManager;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.memory.MemorySystem;
import com.example.agent.prompt.PromptLibrary;
import com.example.agent.prompt.PromptService;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.FileContentService;
import com.example.agent.service.TokenEstimator;
import com.example.agent.mcp.McpServiceManager;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.orchestrator.ToolOrchestrator;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
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
        
        请始终使用中文回复。
        """;

    private final Config config;
    private final Terminal terminal;
    private final LineReader reader;
    
    private LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private ConcurrentToolExecutor concurrentToolExecutor;
    private ToolOrchestrator toolOrchestrator;
    private TokenEstimator tokenEstimator;
    private ConversationManager conversationManager;
    private TokenMetricsCollector tokenMetricsCollector;
    private EventMetricsCollector eventMetricsCollector;
    private RuleManager ruleManager;
    private CacheManager cacheManager;
    private CodeIndex codeIndex;
    private ThinkingEngine thinkingEngine;
    private McpServiceManager mcpServiceManager;
    private AgentMode currentMode = AgentMode.CHAT;
    private final java.util.List<java.util.function.Consumer<AgentMode>> modeChangeListeners = new java.util.ArrayList<>();

    public AgentContext() throws IOException {
        this.config = Config.getInstance();
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter("help", "exit", "quit", "clear", "reset", "retry", "config", "showlog", "tokens", "/mcp", "/mcp list", "/mcp connect", "/mcp disconnect", "/mcp tools", "/chat", "/builder", "/mode", "/mode chat", "/mode builder"))
                .variable(LineReader.HISTORY_FILE, java.nio.file.Paths.get(".agent_history"))
                .build();

        // ✅ 注册快捷键: Shift+Tab 一键切换 Builder/Chat 模式
        registerModeSwitchShortcut();
    }

    private void registerModeSwitchShortcut() {
        reader.getWidgets().put("toggle-mode", () -> {
            AgentMode newMode = (currentMode == AgentMode.CHAT) 
                ? AgentMode.BUILDER 
                : AgentMode.CHAT;
            
            switchMode(newMode);
            
            terminal.puts(InfoCmp.Capability.carriage_return);
            terminal.writer().println();
            terminal.writer().println(ConsoleStyle.boldCyan(String.format(
                "  ⌨️  Shift+Tab 切换到 %s", newMode.getFullDisplayName()
            )));
            terminal.writer().flush();
            
            reader.callWidget(LineReader.REDRAW_LINE);
            return true;
        });
        
        reader.getKeyMaps().get(LineReader.MAIN).bind(
            new Reference("toggle-mode"), 
            "shift-tab"
        );
    }

    public void initialize() {
        LogDirectoryManager.ensureDirectoriesExist();
        LocalDate today = LocalDate.now();
        this.tokenMetricsCollector = new TokenMetricsCollector(today);
        this.eventMetricsCollector = new EventMetricsCollector(today);
        logger.info("日志系统已初始化");

        // ✅ 一行代码初始化所有依赖
        CoreModule.configure();
        logger.info("DI 容器初始化完成 ✅");

        // ✅ 从 DI 容器获取所有依赖，再也不需要手动 new 了！
        this.llmClient = ServiceLocator.get(LlmClient.class);
        this.tokenEstimator = ServiceLocator.get(TokenEstimator.class);
        this.cacheManager = ServiceLocator.get(CacheManager.class);
        this.ruleManager = ServiceLocator.get(RuleManager.class);
        this.codeIndex = ServiceLocator.get(CodeIndex.class);
        this.toolRegistry = ServiceLocator.get(ToolRegistry.class);
        this.concurrentToolExecutor = ServiceLocator.get(ConcurrentToolExecutor.class);

        // 初始化 Tool Orchestrator 工具编排引擎
        this.toolOrchestrator = new ToolOrchestrator(concurrentToolExecutor);
        ServiceLocator.registerSingleton(ToolOrchestrator.class, toolOrchestrator);
        logger.info("ToolOrchestrator 初始化完成 ✅ - {}", toolOrchestrator.getStats());

        // 初始化各种管理器
        this.ruleManager.loadHippoRules();
        this.ruleManager.loadMemoryMd();
        logger.info("RuleManager 初始化完成");

        this.codeIndex.buildIndex();
        logger.info("代码索引构建完成");

        // 初始化 MCP 服务管理器
        this.mcpServiceManager = new McpServiceManager(config, toolRegistry);
        this.mcpServiceManager.initialize();

        // 启动缓存监控
        this.cacheManager.startMemoryMonitor();
        logger.info("缓存监控线程已启动");

        // 初始化 ThinkingEngine
        this.thinkingEngine = ServiceLocator.get(ThinkingEngine.class);
        this.thinkingEngine.setCodeIndex(this.codeIndex);

        logger.info("统一思考引擎 ThinkingEngine 初始化完成 ✅");

        // 初始化 PromptLibrary
        PromptService promptService = new PromptService();
        ServiceLocator.registerSingleton(PromptService.class, promptService);
        ServiceLocator.registerSingleton(PromptLibrary.class, PromptLibrary.getInstance());
        logger.info("PromptLibrary 初始化完成 ✅");

        // 初始化 MemorySystem - 优先级记忆系统
        MemorySystem memorySystem = new MemorySystem(tokenEstimator);
        ServiceLocator.registerSingleton(MemorySystem.class, memorySystem);
        logger.info("MemorySystem 初始化完成 ✅ - {}", memorySystem.getStats());

        // 增强系统提示词（使用 PromptLibrary）
        String basePrompt = promptService.getBasePrompt(null);
        String enhancedSystemPrompt = this.ruleManager.enhanceSystemPrompt(basePrompt);
        this.conversationManager = new ConversationManager(enhancedSystemPrompt, tokenEstimator, config.getContext());

        // 启用优先级记忆策略（可通过配置关闭）
        if (memorySystem.isEnabled()) {
            this.conversationManager.setTrimPolicy(memorySystem.getTrimPolicy());
            logger.info("优先级记忆策略已启用 ✅");
        }

        // 模式切换监听器：自动切换 System Prompt + 状态栏，无缝保留上下文
        onModeChanged(newMode -> {
            com.example.agent.prompt.model.TaskMode taskMode = switch (newMode) {
                case CHAT -> com.example.agent.prompt.model.TaskMode.CHAT;
                case BUILDER -> com.example.agent.prompt.model.TaskMode.CODING;
            };
            String prompt = promptService.getBasePrompt(taskMode);
            String enhancedPrompt = ruleManager.enhanceSystemPrompt(prompt);
            
            // ✅ 核心：preserveHistory = true
            conversationManager.setSystemPrompt(enhancedPrompt, true);
            
            // ✅ 更新 Terminal 状态栏标题
            AgentUi ui = ServiceLocator.getOrNull(AgentUi.class);
            if (ui != null) {
                ui.updateTerminalTitle(newMode);
            }
            
            logger.info("模式无缝切换: {} - 上下文已完整保留", newMode.getFullDisplayName());
        });

        // 默认使用 Chat 模式 Prompt
        String defaultPrompt = promptService.getBasePrompt(com.example.agent.prompt.model.TaskMode.CHAT);
        String enhancedDefaultPrompt = ruleManager.enhanceSystemPrompt(defaultPrompt);
        conversationManager.setSystemPrompt(enhancedDefaultPrompt);
        logger.info("默认启用 Chat 模式 System Prompt ✅");
    }

    public void resetConversation() {
        PromptService promptService = ServiceLocator.get(PromptService.class);
        MemorySystem memorySystem = ServiceLocator.getOrNull(MemorySystem.class);
        String basePrompt = promptService != null ? promptService.getBasePrompt(null) : SYSTEM_PROMPT;

        // 重新加载规则（确保文件有更新时能重新加载）
        if (this.ruleManager != null) {
            this.ruleManager.reload();
            String enhancedSystemPrompt = this.ruleManager.enhanceSystemPrompt(basePrompt);
            this.conversationManager = new ConversationManager(enhancedSystemPrompt, tokenEstimator, config.getContext());
        } else {
            this.conversationManager = new ConversationManager(basePrompt, tokenEstimator, config.getContext());
        }

        // 重置记忆策略
        if (memorySystem != null && memorySystem.isEnabled()) {
            this.conversationManager.setTrimPolicy(memorySystem.getTrimPolicy());
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

    public EventMetricsCollector getEventMetricsCollector() {
        return eventMetricsCollector;
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public FileContentService getFileContentService() {
        return ServiceLocator.get(FileContentService.class);
    }

    public CodeIndex getCodeIndex() {
        return codeIndex;
    }

    public ThinkingEngine getThinkingEngine() {
        return thinkingEngine;
    }

    public McpServiceManager getMcpServiceManager() {
        return mcpServiceManager;
    }

    public AgentMode getCurrentMode() {
        return currentMode;
    }

    public void switchMode(AgentMode newMode) {
        if (currentMode != newMode) {
            AgentMode oldMode = currentMode;
            currentMode = newMode;
            logger.info("模式切换: {} -> {}", oldMode.getDisplayName(), newMode.getDisplayName());
            modeChangeListeners.forEach(listener -> listener.accept(newMode));
        }
    }

    public void onModeChanged(java.util.function.Consumer<AgentMode> listener) {
        modeChangeListeners.add(listener);
    }

    public void close() {
        logger.info("开始清理 AgentContext 资源...");

        if (mcpServiceManager != null) {
            mcpServiceManager.shutdown();
        }

        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
            logger.error("关闭终端失败", e);
        }

        logger.info("AgentContext 资源清理完成 ✅");
    }
}
