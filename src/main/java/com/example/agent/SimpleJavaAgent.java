package com.example.agent;

import com.example.agent.config.Config;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.console.InputHandler;
import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.llm.client.DefaultLlmClient;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.llm.model.Usage;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.logging.LogDirectoryManager;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import com.example.agent.tools.*;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.tools.concurrent.ToolExecutionResult;
import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class SimpleJavaAgent {

    private static final Logger logger = LoggerFactory.getLogger(SimpleJavaAgent.class);

    private static final String SYSTEM_PROMPT = """
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

    private static final int MAX_RETRIES = 3;

    private Config config;
    private LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private ConcurrentToolExecutor concurrentToolExecutor;
    private TokenEstimator tokenEstimator;
    private ConversationManager conversationManager;
    private InputHandler inputHandler;
    private Terminal terminal;
    private LineReader reader;
    private int conversationRound = 0;
    private volatile boolean interrupted = false;
    
    private ConversationLogger conversationLogger;
    private TokenMetricsCollector tokenMetricsCollector;
    private String currentConversationId;

    public static void main(String[] args) {
        SimpleJavaAgent agent = new SimpleJavaAgent();
        agent.run();
    }

    public void run() {
        config = Config.getInstance();

        try (Terminal term = TerminalBuilder.builder()
                .system(true)
                .build()) {

            this.terminal = term;

            if (!validateConfig()) {
                return;
            }

            llmClient = new DefaultLlmClient(config);
            toolRegistry = createToolRegistry();
            concurrentToolExecutor = new ConcurrentToolExecutor(toolRegistry);
            tokenEstimator = new TokenEstimator();
            conversationManager = new ConversationManager(SYSTEM_PROMPT, tokenEstimator);
            
            LogDirectoryManager.ensureDirectoriesExist();
            tokenMetricsCollector = new TokenMetricsCollector(LocalDate.now());
            logger.info("日志系统已初始化");

            Completer completer = new StringsCompleter("help", "exit", "quit", "clear", "reset", "retry", "config", "showlog", "tokens");

            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .variable(LineReader.HISTORY_FILE, java.nio.file.Paths.get(".agent_history"))
                    .build();
            
            terminal.handle(Terminal.Signal.INT, signal -> {
                interrupted = true;
            });

            inputHandler = new InputHandler(reader, tokenEstimator);

            printWelcome();

            while (true) {
                try {
                    String line = reader.readLine(ConsoleStyle.prompt());
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                        println(ConsoleStyle.cyan("再见！"));
                        break;
                    }

                    if ("help".equalsIgnoreCase(line)) {
                        printHelp();
                        continue;
                    }

                    if ("clear".equalsIgnoreCase(line)) {
                        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                        terminal.flush();
                        continue;
                    }

                    if ("reset".equalsIgnoreCase(line)) {
                        conversationManager.reset();
                        println(ConsoleStyle.success("会话已重置"));
                        println();
                        continue;
                    }

                    if ("config".equalsIgnoreCase(line)) {
                        printConfig();
                        continue;
                    }
                    
                    if ("tokens".equalsIgnoreCase(line)) {
                        tokenMetricsCollector.printDailySummary();
                        continue;
                    }
                    
                    if ("showlog".equalsIgnoreCase(line)) {
                        showLastConversationLog();
                        continue;
                    }

                    if ("\"\"\"".equals(line) || "multi".equalsIgnoreCase(line)) {
                        line = inputHandler.readMultilineInput();
                        if (line == null || line.trim().isEmpty()) {
                            continue;
                        }
                    }

                    processUserInput(line);

                } catch (UserInterruptException e) {
                    if (interrupted) {
                        println();
                        println(ConsoleStyle.yellow("对话已中断，开始新对话"));
                        println();
                        interrupted = false;
                    } else {
                        println(ConsoleStyle.red("^C"));
                        println(ConsoleStyle.cyan("再见！"));
                        break;
                    }
                } catch (EndOfFileException e) {
                    println(ConsoleStyle.cyan("再见！"));
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println(ConsoleStyle.error("终端错误: " + e.getMessage()));
        }
    }

    private boolean validateConfig() {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            println(ConsoleStyle.red("╔══════════════════════════════════════════════════╗"));
            println(ConsoleStyle.red("║           API Key 未配置                         ║"));
            println(ConsoleStyle.red("╚══════════════════════════════════════════════════╝"));
            println();
            println(ConsoleStyle.yellow("请在配置文件中设置您的 API Key:"));
            println(ConsoleStyle.gray("  文件位置: " + config.getConfigFilePath()));
            println();
            println(ConsoleStyle.gray("支持的配置格式: config.yaml, config.yml, config.json"));
            println();
            println(ConsoleStyle.gray("YAML 配置示例 (config.yaml):"));
            println(ConsoleStyle.cyan("  llm:"));
            println(ConsoleStyle.cyan("    api_key: ${DASHSCOPE_API_KEY}"));
            println(ConsoleStyle.cyan("    model: qwen3.5-plus"));
            println(ConsoleStyle.cyan("    base_url: https://dashscope.aliyuncs.com"));
            println();
            println(ConsoleStyle.gray("或设置环境变量: DASHSCOPE_API_KEY 或 OPENAI_API_KEY"));
            println();
            return false;
        }

        if (config.getApiKey().equals("your-api-key-here")) {
            println(ConsoleStyle.yellow("警告: API Key 仍为默认值，请修改配置文件"));
            println(ConsoleStyle.gray("配置文件位置: " + config.getConfigFilePath()));
            println();
            println(ConsoleStyle.yellow("是否继续？(y/n)"));
            
            try {
                String input = System.console() != null ? 
                    new String(System.console().readPassword()) : 
                    new java.util.Scanner(System.in).nextLine();
                
                if (!"y".equalsIgnoreCase(input.trim())) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    private void printWelcome() {
        println();
        println(ConsoleStyle.boldCyan("╔═════════════════════════════════════════════╗"));
        println(ConsoleStyle.boldCyan("║       Code Agent - AI 编程助手               ║"));
        println(ConsoleStyle.boldCyan("╚═════════════════════════════════════════════╝"));
        println();
        println(ConsoleStyle.info("模型: " + config.getModel()));
        println(ConsoleStyle.info("API: " + config.getBaseUrl()));
        println();
        println(ConsoleStyle.bold("快捷命令:"));
        println(ConsoleStyle.green("  help  ") + ConsoleStyle.gray(" - 显示帮助"));
        println(ConsoleStyle.green("  multi ") + ConsoleStyle.gray(" - 多行输入模式（粘贴代码/日志）"));
        println(ConsoleStyle.green("  reset ") + ConsoleStyle.gray(" - 重置会话"));
        println(ConsoleStyle.green("  exit  ") + ConsoleStyle.gray(" - 退出程序"));
        println();
        println(ConsoleStyle.yellow("提示: 粘贴多行内容请先输入 \"\"\" 或 multi"));
        println();
    }

    private void printConfig() {
        println(ConsoleStyle.bold("当前配置:"));
        println(ConsoleStyle.green("  配置文件: ") + config.getConfigFilePath());
        println();
        println(ConsoleStyle.boldCyan("  [LLM 配置]"));
        println(ConsoleStyle.green("  Provider: ") + config.getLlm().getProvider());
        println(ConsoleStyle.green("  模型: ") + config.getModel());
        println(ConsoleStyle.green("  API: ") + config.getBaseUrl());
        println(ConsoleStyle.green("  MaxTokens: ") + config.getMaxTokens());
        println(ConsoleStyle.green("  Temperature: ") + config.getLlm().getTemperature());
        println(ConsoleStyle.green("  Timeout: ") + config.getLlm().getTimeout() + "ms");
        println(ConsoleStyle.green("  API Key: ") + maskApiKey(config.getApiKey()));
        println();
        println(ConsoleStyle.boldCyan("  [工具配置]"));
        println(ConsoleStyle.green("  Bash: ") + (config.getTools().getBash().isEnabled() ? "启用" : "禁用"));
        println(ConsoleStyle.green("  File: ") + (config.getTools().getFile().isEnabled() ? "启用" : "禁用"));
        println();
        println(ConsoleStyle.boldCyan("  [会话配置]"));
        println(ConsoleStyle.green("  自动保存: ") + (config.getSession().isAutoSave() ? "是" : "否"));
        println(ConsoleStyle.green("  最大历史: ") + config.getSession().getMaxHistory());
        println();
        println(ConsoleStyle.boldCyan("  [UI 配置]"));
        println(ConsoleStyle.green("  主题: ") + config.getUi().getTheme());
        println(ConsoleStyle.green("  提示符: ") + config.getUi().getPrompt());
        println(ConsoleStyle.green("  语法高亮: ") + (config.getUi().isSyntaxHighlight() ? "是" : "否"));
        println();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    private void showLastConversationLog() {
        if (currentConversationId == null) {
            println(ConsoleStyle.yellow("还没有对话记录"));
            println();
            return;
        }
        
        Path logFile = LogDirectoryManager.getConversationLogFile(currentConversationId, LocalDate.now());
        
        if (!java.nio.file.Files.exists(logFile)) {
            println(ConsoleStyle.yellow("日志文件不存在: " + logFile));
            println();
            return;
        }
        
        try {
            println(ConsoleStyle.bold("最近一次对话日志:"));
            println(ConsoleStyle.gray("─".repeat(80)));
            
            java.util.List<String> lines = java.nio.file.Files.readAllLines(logFile);
            int maxLines = Math.min(50, lines.size());
            
            for (int i = 0; i < maxLines; i++) {
                println(lines.get(i));
            }
            
            if (lines.size() > 50) {
                println(ConsoleStyle.gray("... (已省略 " + (lines.size() - 50) + " 行)"));
            }
            
            println(ConsoleStyle.gray("─".repeat(80)));
            println(ConsoleStyle.dim("日志文件: " + logFile));
            println();
        } catch (IOException e) {
            println(ConsoleStyle.red("读取日志文件失败: " + e.getMessage()));
            println();
        }
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

    private void printHelp() {
        println(ConsoleStyle.bold("可用命令:"));
        println(ConsoleStyle.green("  help    ") + " - 显示帮助信息");
        println(ConsoleStyle.green("  clear   ") + " - 清屏");
        println(ConsoleStyle.green("  reset   ") + " - 重置会话历史");
        println(ConsoleStyle.green("  config  ") + " - 显示当前配置");
        println(ConsoleStyle.green("  tokens  ") + " - 显示今日 Token 消耗统计");
        println(ConsoleStyle.green("  showlog ") + " - 显示最近一次对话日志");
        println(ConsoleStyle.green("  multi   ") + " - 进入多行输入模式");
        println(ConsoleStyle.green("  exit    ") + " - 退出程序");
        println(ConsoleStyle.green("  quit    ") + " - 退出程序");
        println();
        println(ConsoleStyle.gray("其他输入将发送给 AI 模型处理。"));
        println(ConsoleStyle.gray("AI 可以使用 read_file 和 write_file 工具来读写文件。"));
        println();
        println(ConsoleStyle.boldYellow("多行输入:"));
        println(ConsoleStyle.gray("  输入 \"\"\" 或 multi 进入多行模式"));
        println(ConsoleStyle.gray("  适合粘贴代码、日志等长文本"));
        println(ConsoleStyle.gray("  再次输入 \"\"\" 结束多行输入"));
        println();
    }

    private void println() {
        terminal.writer().println();
        terminal.writer().flush();
    }

    private void println(String text) {
        terminal.writer().println(text);
        terminal.writer().flush();
    }

    private void print(String text) {
        terminal.writer().print(text);
        terminal.writer().flush();
    }

    private void processUserInput(String userInput) {
        interrupted = false;
        conversationRound++;
        
        currentConversationId = String.valueOf(System.currentTimeMillis());
        Path logFile = LogDirectoryManager.getConversationLogFile(currentConversationId, LocalDate.now());
        conversationLogger = new ConversationLogger(currentConversationId, logFile);
        
        int inputTokens = tokenEstimator.estimateTextTokens(userInput);
        if (inputTokens > inputHandler.getMaxInputTokens()) {
            userInput = inputHandler.handleLongInput(userInput, inputTokens);
            if (userInput == null) {
                conversationRound--;
                return;
            }
        }
        
        conversationLogger.logUserInput(userInput, inputTokens);
        
        conversationManager.addUserMessage(userInput);
        conversationManager.trimHistory((messageCount, tokenCount) -> {
            println(ConsoleStyle.gray("  [历史已精简: ") + ConsoleStyle.yellow(String.valueOf(messageCount)) + ConsoleStyle.gray(" 条消息, 约 ") + ConsoleStyle.yellow(String.valueOf(tokenCount)) + ConsoleStyle.gray(" tokens]"));
            println();
        });
        
        println();
        println(ConsoleStyle.conversationDivider(conversationRound));
        println();
        println(ConsoleStyle.userLabel() + ": " + ConsoleStyle.white(userInput));
        println();
        
        processAgentLoop();
    }

    private void processAgentLoop() {
        while (!interrupted) {
            try {
                println(ConsoleStyle.gray("  ┌─ ") + ConsoleStyle.boldCyan("AI 思考中..."));
                println(ConsoleStyle.gray("  │"));
                print(ConsoleStyle.gray("  └─ ") + ConsoleStyle.boldCyan("AI: "));
                println();

                StringBuilder contentBuilder = new StringBuilder();

                ChatResponse response = llmClient.chatStream(
                    conversationManager.getHistory(), 
                    toolRegistry.toTools(),
                    chunk -> {
                        if (interrupted) {
                            throw new RuntimeException("Interrupted");
                        }
                        if (chunk.hasContent()) {
                            print(ConsoleStyle.white(chunk.getContent()));
                            contentBuilder.append(chunk.getContent());
                        }
                    }
                );

                if (interrupted) {
                    println();
                    println();
                    println(ConsoleStyle.gray("  │"));
                    println(ConsoleStyle.yellow("  └─ 对话已中断"));
                    throw new UserInterruptException("User interrupted");
                }

                println();

                Message assistantMessage = response.getFirstMessage();
                if (assistantMessage == null) {
                    println();
                    println(ConsoleStyle.gray("  │"));
                    println(ConsoleStyle.gray("  └─ ") + ConsoleStyle.red("未收到有效响应"));
                    break;
                }

                if (response.hasToolCalls()) {
                    conversationManager.addAssistantMessage(assistantMessage);

                    List<ToolCall> toolCalls = assistantMessage.getToolCalls();
                    println();
                    println(ConsoleStyle.gray("  │"));
                    println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.boldYellow("工具调用:"));

                    if (interrupted) {
                        println();
                        println(ConsoleStyle.yellow("  └─ 工具调用已中断"));
                        throw new UserInterruptException("User interrupted");
                    }

                    processToolCallsConcurrently(toolCalls);
                    
                    println(ConsoleStyle.gray("  │"));
                } else {
                    conversationManager.addAssistantMessage(assistantMessage);
                    
                    Usage usage = response.getUsage();
                    conversationLogger.logAiResponse(contentBuilder.toString(), usage);
                    
                    if (usage != null) {
                        tokenMetricsCollector.recordConversation(
                            currentConversationId,
                            java.time.LocalDateTime.now(),
                            conversationManager.getTokenCount(),
                            usage
                        );
                    }
                    
                    conversationLogger.logSummary();
                    
                    println();
                    println(ConsoleStyle.gray(" ---  ") + ConsoleStyle.green("完成✅"));
                    println();
                    println(ConsoleStyle.conversationEnd());
                    println();
                    break;
                }

            } catch (LlmException e) {
                handleApiError(e);
                break;
            } catch (RuntimeException e) {
                if ("Interrupted".equals(e.getMessage())) {
                    throw new UserInterruptException("User interrupted");
                }
                println();
                println(ConsoleStyle.gray("  │"));
                println(ConsoleStyle.gray("  └─ ") + ConsoleStyle.red("处理错误: " + e.getMessage()));
                e.printStackTrace();
                break;
            } catch (Exception e) {
                println();
                println(ConsoleStyle.gray("  │"));
                println(ConsoleStyle.gray("  └─ ") + ConsoleStyle.red("处理错误: " + e.getMessage()));
                e.printStackTrace();
                break;
            }
        }
    }

    private void handleApiError(LlmException e) throws UserInterruptException {
        // 检查是否是用户中断
        String message = e.getMessage();
        if (message != null && message.contains("Interrupted")) {
            println();
            println(ConsoleStyle.yellow("用户已终止对话"));
            println();
            throw new UserInterruptException("User interrupted");
        }
        
        println();
        println(ConsoleStyle.red("╔══════════════════════════════════════════════════╗"));
        println(ConsoleStyle.red("║                  API 调用失败                    ║"));
        println(ConsoleStyle.red("╚══════════════════════════════════════════════════╝"));
        println();
        println(ConsoleStyle.red("错误信息: " + e.getMessage()));
        println();

        if (isRetryableError(e)) {
            println(ConsoleStyle.yellow("您可以:"));
            println(ConsoleStyle.gray("  1. 输入 'retry' 重试"));
            println(ConsoleStyle.gray("  2. 输入 'reset' 重置会话"));
            println(ConsoleStyle.gray("  3. 输入 'config' 检查配置"));
            println(ConsoleStyle.gray("  4. 继续输入其他内容"));
            println();
        }
    }

    private boolean isRetryableError(LlmException e) {
        if (e instanceof LlmTimeoutException) {
            return true;
        }
        
        if (e instanceof LlmConnectionException) {
            return true;
        }
        
        if (e instanceof LlmApiException) {
            LlmApiException apiException = (LlmApiException) e;
            return apiException.isServerError() || apiException.isRateLimited();
        }
        
        return false;
    }

    private void processToolCallsConcurrently(List<ToolCall> toolCalls) {
        long startTime = System.currentTimeMillis();
        
        java.util.Map<String, String> argumentsMap = new java.util.HashMap<>();
        for (ToolCall toolCall : toolCalls) {
            argumentsMap.put(toolCall.getId(), toolCall.getFunction().getArguments());
        }
        
        List<ToolExecutionResult> results = concurrentToolExecutor.executeConcurrently(toolCalls);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        for (ToolExecutionResult result : results) {
            String arguments = argumentsMap.get(result.getToolCallId());
            
            if (result.isSuccess()) {
                println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.toolCall(result.getToolName(), "成功"));
                String displayResult = truncate(result.getResult(), 100);
                println(ConsoleStyle.gray("  │  └─ ") + ConsoleStyle.dim(displayResult));
                
                conversationLogger.logToolCall(
                    result.getToolName(),
                    arguments != null ? arguments : "{}",
                    result.getResult(),
                    result.getExecutionTimeMs(),
                    true
                );
                
                conversationManager.addToolResult(result.getToolCallId(), result.getToolName(), result.getResult());
            } else {
                println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.toolCall(result.getToolName(), "失败"));
                println(ConsoleStyle.gray("  │  └─ ") + ConsoleStyle.red(result.getErrorMessage()));
                
                conversationLogger.logToolCall(
                    result.getToolName(),
                    arguments != null ? arguments : "{}",
                    result.getErrorMessage(),
                    result.getExecutionTimeMs(),
                    false
                );
                
                String errorResult = "Error: " + result.getErrorMessage() + "\nPlease try a different approach or check if the path is correct.";
                conversationManager.addToolResult(result.getToolCallId(), result.getToolName(), errorResult);
            }
        }
        
        if (toolCalls.size() > 1) {
            println(ConsoleStyle.gray("  │"));
            println(ConsoleStyle.gray("  │  ") + ConsoleStyle.dim(
                String.format("并发执行 %d 个工具，总耗时 %d ms", toolCalls.size(), totalTime)));
        }
    }

    private void processToolCall(ToolCall toolCall) {
        String toolName = toolCall.getFunction().getName();
        String arguments = toolCall.getFunction().getArguments();
        String toolCallId = toolCall.getId();

        println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.toolCall(toolName, "执行中..."));

        try {
            String result = toolRegistry.execute(toolName, arguments);
            String displayResult = truncate(result, 100);
            println(ConsoleStyle.gray("  │  └─ ") + ConsoleStyle.dim(displayResult));

            conversationManager.addToolResult(toolCallId, toolName, result);

        } catch (ToolExecutionException e) {
            String errorMessage = "工具执行失败: " + e.getMessage();
            println(ConsoleStyle.gray("  │  └─ ") + ConsoleStyle.red(errorMessage));

            String errorResult = "Error: " + e.getMessage() + "\nPlease try a different approach or check if the path is correct.";
            conversationManager.addToolResult(toolCallId, toolName, errorResult);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace("\n", " ").replace("\r", "");
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        return singleLine.substring(0, maxLength) + "...";
    }
}
