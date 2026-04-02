package com.example.agent;

import com.example.agent.config.Config;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.console.InputHandler;
import com.example.agent.llm.*;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import com.example.agent.tools.*;
import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;

public class SimpleJavaAgent {

    private static final String SYSTEM_PROMPT = """
        你是一个编程助手，可以帮助用户进行软件开发任务。
        
        你可以访问以下工具：
        - read_file: 读取文件内容
        - write_file: 写入文件内容
        
        当用户请求涉及文件操作时，请使用相应的工具完成任务。
        在修改文件之前，请先读取文件内容了解当前状态。
        完成任务后，请简要说明你做了什么。
        
        请始终使用中文回复。
        """;

    private static final int MAX_RETRIES = 3;

    private Config config;
    private LlmClient llmClient;
    private ToolRegistry toolRegistry;
    private TokenEstimator tokenEstimator;
    private ConversationManager conversationManager;
    private InputHandler inputHandler;
    private Terminal terminal;
    private LineReader reader;
    private int conversationRound = 0;

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
            tokenEstimator = new TokenEstimator();
            conversationManager = new ConversationManager(SYSTEM_PROMPT, tokenEstimator);

            Completer completer = new StringsCompleter("help", "exit", "quit", "clear", "reset", "retry", "config");

            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .variable(LineReader.HISTORY_FILE, java.nio.file.Paths.get(".agent_history"))
                    .build();

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

                    if ("\"\"\"".equals(line) || "multi".equalsIgnoreCase(line)) {
                        line = inputHandler.readMultilineInput();
                        if (line == null || line.trim().isEmpty()) {
                            continue;
                        }
                    }

                    processUserInput(line);

                } catch (UserInterruptException e) {
                    println(ConsoleStyle.red("^C"));
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
            println(ConsoleStyle.yellow("请在 config.json 中设置您的 API Key:"));
            println(ConsoleStyle.gray("  文件位置: " + getConfigFilePath()));
            println();
            println(ConsoleStyle.gray("配置示例:"));
            println(ConsoleStyle.cyan("  {"));
            println(ConsoleStyle.cyan("    \"apiKey\" : \"sk-your-api-key-here\","));
            println(ConsoleStyle.cyan("    \"model\" : \"qwen3.5-plus\","));
            println(ConsoleStyle.cyan("    \"baseUrl\" : \"https://dashscope.aliyuncs.com\""));
            println(ConsoleStyle.cyan("  }"));
            println();
            return false;
        }

        if (config.getApiKey().equals("your-api-key-here")) {
            println(ConsoleStyle.yellow("警告: API Key 仍为默认值，请修改 config.json"));
            println(ConsoleStyle.gray("配置文件位置: " + getConfigFilePath()));
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

    private String getConfigFilePath() {
        try {
            return new java.io.File("config.json").getAbsolutePath();
        } catch (Exception e) {
            return "config.json";
        }
    }

    private void printWelcome() {
        println();
        println(ConsoleStyle.boldCyan("╔════════════════════════════════════════════════════╗"));
        println(ConsoleStyle.boldCyan("║       Simple Java Agent - AI 编程助手                ║"));
        println(ConsoleStyle.boldCyan("╚════════════════════════════════════════════════════╝"));
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
        println(ConsoleStyle.green("  模型: ") + config.getModel());
        println(ConsoleStyle.green("  API: ") + config.getBaseUrl());
        println(ConsoleStyle.green("  MaxTokens: ") + config.getMaxTokens());
        println(ConsoleStyle.green("  API Key: ") + maskApiKey(config.getApiKey()));
        println();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        return registry;
    }

    private void printHelp() {
        println(ConsoleStyle.bold("可用命令:"));
        println(ConsoleStyle.green("  help   ") + " - 显示帮助信息");
        println(ConsoleStyle.green("  clear  ") + " - 清屏");
        println(ConsoleStyle.green("  reset  ") + " - 重置会话历史");
        println(ConsoleStyle.green("  config ") + " - 显示当前配置");
        println(ConsoleStyle.green("  multi  ") + " - 进入多行输入模式");
        println(ConsoleStyle.green("  exit   ") + " - 退出程序");
        println(ConsoleStyle.green("  quit   ") + " - 退出程序");
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
        conversationRound++;
        
        int inputTokens = tokenEstimator.estimateTextTokens(userInput);
        if (inputTokens > inputHandler.getMaxInputTokens()) {
            userInput = inputHandler.handleLongInput(userInput, inputTokens);
            if (userInput == null) {
                conversationRound--;
                return;
            }
        }
        
        conversationManager.addUserMessage(userInput);
        conversationManager.trimHistory((messageCount, tokenCount) -> {
            println(ConsoleStyle.gray("[历史已精简: " + messageCount + " 条消息, 约 " + tokenCount + " tokens]"));
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
        while (true) {
            try {
                print(ConsoleStyle.gray("  "));
                println(ConsoleStyle.thinking());

                ChatResponse response = llmClient.chat(conversationManager.getHistory(), toolRegistry.toTools());

                Message assistantMessage = response.getFirstMessage();
                if (assistantMessage == null) {
                    println(ConsoleStyle.error("未收到有效响应"));
                    break;
                }

                if (assistantMessage.getContent() != null && !assistantMessage.getContent().isEmpty()) {
                    println();
                    println(ConsoleStyle.aiLabel() + ":");
                    println(ConsoleStyle.white(assistantMessage.getContent()));
                }

                if (response.hasToolCalls()) {
                    conversationManager.addAssistantMessage(assistantMessage);

                    List<ToolCall> toolCalls = assistantMessage.getToolCalls();
                    println();

                    for (ToolCall toolCall : toolCalls) {
                        processToolCall(toolCall);
                    }
                } else {
                    conversationManager.addAssistantMessage(assistantMessage);
                    println();
                    println(ConsoleStyle.conversationEnd());
                    println();
                    break;
                }

            } catch (LlmException e) {
                handleApiError(e);
                break;
            } catch (Exception e) {
                println(ConsoleStyle.error("处理错误: " + e.getMessage()));
                e.printStackTrace();
                break;
            }
        }
    }

    private void handleApiError(LlmException e) {
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
