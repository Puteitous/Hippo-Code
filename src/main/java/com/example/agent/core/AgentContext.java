package com.example.agent.core;

import com.example.agent.config.Config;
import com.example.agent.context.config.ContextConfig;
import com.example.agent.context.memory.HotMemory;
import com.example.agent.context.memory.WarmMemory;
import com.example.agent.context.memory.ColdMemory;
import com.example.agent.llm.client.DefaultLlmClient;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.logging.LogDirectoryManager;
import com.example.agent.logging.TokenMetricsCollector;
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
        - read_file: 读取文件内容
        - write_file: 写入文件内容（覆盖整个文件）
        - edit_file: 精确编辑文件内容（替换特定文本片段）
        - list_directory: 列出目录内容，支持递归显示目录树
        - glob: 使用 glob 模式查找文件（如 **/*.java 查找所有 Java 文件）
        - grep: 在文件内容中搜索文本（支持正则表达式）
        - ask_user: 向用户提问并等待回答（用于确认或获取信息）
        - bash: 执行终端命令（如 git, mvn, npm 等，有安全限制）
        
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
    private HotMemory hotMemory;
    private WarmMemory warmMemory;
    private ColdMemory coldMemory;

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
        this.toolRegistry = createToolRegistry();
        this.concurrentToolExecutor = new ConcurrentToolExecutor(toolRegistry);
        this.tokenEstimator = TokenEstimatorFactory.create(config);
        
        // 初始化 HotMemory
        this.hotMemory = new HotMemory(tokenEstimator, config.getContext().getHotMemory());
        this.hotMemory.loadHippoRules();
        this.hotMemory.loadMemoryMd();
        
        // 初始化 WarmMemory
        this.warmMemory = new WarmMemory(tokenEstimator, config.getContext().getWarmMemory());
        
        // 初始化 ColdMemory
        this.coldMemory = new ColdMemory(tokenEstimator, config.getContext().getColdMemory());
        
        // 增强系统提示词
        String enhancedSystemPrompt = this.hotMemory.enhanceSystemPrompt(SYSTEM_PROMPT);
        this.conversationManager = new ConversationManager(enhancedSystemPrompt, tokenEstimator, config.getContext());
    }

    private ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new ListDirectoryTool());
        registry.register(new GlobTool());
        registry.register(new GrepTool());
        registry.register(new EditFileTool());
        registry.register(new AskUserTool());
        registry.register(new BashTool());
        return registry;
    }

    public void resetConversation() {
        // 重新加载 HotMemory（确保文件有更新时能重新加载）
        if (this.hotMemory != null) {
            this.hotMemory.reload();
            String enhancedSystemPrompt = this.hotMemory.enhanceSystemPrompt(SYSTEM_PROMPT);
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

    public HotMemory getHotMemory() {
        return hotMemory;
    }

    public WarmMemory getWarmMemory() {
        return warmMemory;
    }

    public ColdMemory getColdMemory() {
        return coldMemory;
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
