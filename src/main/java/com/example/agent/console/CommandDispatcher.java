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

    public enum CommandResult {
        CONTINUE,
        EXIT,
        PROCESS_INPUT
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
            return CommandResult.CONTINUE;
        }
        
        line = line.trim();

        if (line.isEmpty()) {
            return CommandResult.CONTINUE;
        }

        if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
            ui.printGoodbye();
            return CommandResult.EXIT;
        }

        if ("help".equalsIgnoreCase(line)) {
            ui.printHelp();
            return CommandResult.CONTINUE;
        }

        if ("clear".equalsIgnoreCase(line)) {
            ui.clearScreen();
            return CommandResult.CONTINUE;
        }

        if ("reset".equalsIgnoreCase(line)) {
            conversationManager.reset();
            ui.println(ConsoleStyle.success("会话已重置"));
            ui.println();
            return CommandResult.CONTINUE;
        }

        if ("config".equalsIgnoreCase(line)) {
            ui.printConfig();
            return CommandResult.CONTINUE;
        }

        if ("tokens".equalsIgnoreCase(line)) {
            tokenMetricsCollector.printDailySummary();
            return CommandResult.CONTINUE;
        }

        if ("showlog".equalsIgnoreCase(line)) {
            if (currentConversationId == null) {
                ui.println(ConsoleStyle.yellow("还没有对话记录"));
                ui.println(ConsoleStyle.gray("提示：开始一次对话后，可以使用 showlog 查看日志"));
                ui.println();
            } else {
                ui.showLastConversationLog(currentConversationId);
            }
            return CommandResult.CONTINUE;
        }

        if ("\"\"\"".equals(line) || "multi".equalsIgnoreCase(line)) {
            return handleMultilineInput();
        }

        return CommandResult.PROCESS_INPUT;
    }

    private CommandResult handleMultilineInput() {
        String line = inputHandler.readMultilineInput();
        if (line == null || line.trim().isEmpty()) {
            return CommandResult.CONTINUE;
        }
        return CommandResult.PROCESS_INPUT;
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
