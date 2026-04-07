package com.example.agent.console;

import com.example.agent.config.Config;
import com.example.agent.core.AgentContext;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.service.ConversationManager;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.util.Scanner;
import java.util.function.Consumer;

public class CommandDispatcher {

    public static class CommandResult {
        public enum Type {
            CONTINUE,
            EXIT,
            PROCESS_INPUT
        }
        
        private final Type type;
        private final String input; // 用于存储实际要处理的输入内容
        
        private CommandResult(Type type, String input) {
            this.type = type;
            this.input = input;
        }
        
        public static CommandResult continueExecution() {
            return new CommandResult(Type.CONTINUE, null);
        }
        
        public static CommandResult exit() {
            return new CommandResult(Type.EXIT, null);
        }
        
        public static CommandResult processInput(String input) {
            return new CommandResult(Type.PROCESS_INPUT, input);
        }
        
        public Type getType() {
            return type;
        }
        
        public String getInput() {
            return input;
        }
    }

    private final AgentUi ui;
    private final Config config;
    private final InputHandler inputHandler;
    private final ConversationManager conversationManager;
    private final TokenMetricsCollector tokenMetricsCollector;
    private String currentConversationId;

    public CommandDispatcher(AgentContext context, AgentUi ui, InputHandler inputHandler) {
        this.ui = ui;
        this.config = context.getConfig();
        this.inputHandler = inputHandler;
        this.conversationManager = context.getConversationManager();
        this.tokenMetricsCollector = context.getTokenMetricsCollector();
    }

    public CommandResult dispatch(String line) throws UserInterruptException, EndOfFileException {
        if (line == null) {
            return CommandResult.continueExecution();
        }
        
        line = line.trim();

        if (line.isEmpty()) {
            return CommandResult.continueExecution();
        }

        if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
            ui.printGoodbye();
            return CommandResult.exit();
        }

        if ("help".equalsIgnoreCase(line)) {
            ui.printHelp();
            return CommandResult.continueExecution();
        }

        if ("clear".equalsIgnoreCase(line)) {
            ui.clearScreen();
            return CommandResult.continueExecution();
        }

        if ("reset".equalsIgnoreCase(line)) {
            conversationManager.reset();
            ui.println(ConsoleStyle.success("会话已重置"));
            ui.println();
            return CommandResult.continueExecution();
        }

        if ("config".equalsIgnoreCase(line)) {
            ui.printConfig();
            return CommandResult.continueExecution();
        }

        if ("tokens".equalsIgnoreCase(line)) {
            tokenMetricsCollector.printDailySummary();
            return CommandResult.continueExecution();
        }

        if ("showlog".equalsIgnoreCase(line)) {
            if (currentConversationId == null) {
                ui.println(ConsoleStyle.yellow("还没有对话记录"));
                ui.println(ConsoleStyle.gray("提示：开始一次对话后，可以使用 showlog 查看日志"));
                ui.println();
            } else {
                ui.showLastConversationLog(currentConversationId);
            }
            return CommandResult.continueExecution();
        }

        if ("\"\"\"".equals(line) || "multi".equalsIgnoreCase(line)) {
            return handleMultilineInput();
        }

        return CommandResult.processInput(line);
    }

    private CommandResult handleMultilineInput() {
        String multilineInput = inputHandler.readMultilineInput();
        if (multilineInput == null || multilineInput.trim().isEmpty()) {
            return CommandResult.continueExecution();
        }
        return CommandResult.processInput(multilineInput);
    }

    public boolean validateConfig() {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            ui.printConfigValidationError();
            return false;
        }

        if ("your-api-key-here".equals(config.getApiKey())) {
            ui.printDefaultApiKeyWarning();

            try {
                String input = System.console() != null
                        ? new String(System.console().readPassword())
                        : new Scanner(System.in).nextLine();

                if (input == null || !"y".equalsIgnoreCase(input.trim())) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    public void setCurrentConversationId(String conversationId) {
        this.currentConversationId = conversationId;
    }

    public String getCurrentConversationId() {
        return currentConversationId;
    }
}
