package com.example.agent.console;

import com.example.agent.config.Config;
import com.example.agent.core.AgentContext;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.service.ConversationManager;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionStorage;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

public class CommandDispatcher {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static class CommandResult {
        public enum Type {
            CONTINUE,
            EXIT,
            PROCESS_INPUT,
            RESUME_SESSION
        }
        
        private final Type type;
        private final String input;
        private final SessionData sessionToResume;
        
        private CommandResult(Type type, String input) {
            this(type, input, null);
        }
        
        private CommandResult(Type type, String input, SessionData session) {
            this.type = type;
            this.input = input;
            this.sessionToResume = session;
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
        
        public static CommandResult resumeSession(SessionData session) {
            return new CommandResult(Type.RESUME_SESSION, null, session);
        }
        
        public Type getType() {
            return type;
        }
        
        public String getInput() {
            return input;
        }
        
        public SessionData getSessionToResume() {
            return sessionToResume;
        }
    }

    private final AgentUi ui;
    private final Config config;
    private final InputHandler inputHandler;
    private final ConversationManager conversationManager;
    private final TokenMetricsCollector tokenMetricsCollector;
    private final SessionStorage sessionStorage;
    private String currentConversationId;

    public CommandDispatcher(AgentContext context, AgentUi ui, InputHandler inputHandler) {
        this.ui = ui;
        this.config = context.getConfig();
        this.inputHandler = inputHandler;
        this.conversationManager = context.getConversationManager();
        this.tokenMetricsCollector = context.getTokenMetricsCollector();
        this.sessionStorage = new SessionStorage();
    }

    public CommandDispatcher(AgentContext context, AgentUi ui, InputHandler inputHandler, SessionStorage sessionStorage) {
        this.ui = ui;
        this.config = context.getConfig();
        this.inputHandler = inputHandler;
        this.conversationManager = context.getConversationManager();
        this.tokenMetricsCollector = context.getTokenMetricsCollector();
        this.sessionStorage = sessionStorage;
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

        if ("sessions".equalsIgnoreCase(line)) {
            return handleListSessions();
        }

        if (line.toLowerCase().startsWith("resume")) {
            return handleResumeCommand(line);
        }

        if (line.toLowerCase().startsWith("delete-session")) {
            return handleDeleteSessionCommand(line);
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

    private CommandResult handleListSessions() {
        List<SessionData> sessions = sessionStorage.listSessions();
        
        if (sessions.isEmpty()) {
            ui.println(ConsoleStyle.yellow("没有保存的会话"));
            ui.println();
            return CommandResult.continueExecution();
        }
        
        ui.println(ConsoleStyle.cyan("╔══════════════════════════════════════════════════════════════╗"));
        ui.println(ConsoleStyle.cyan("║                      📋 已保存的会话                          ║"));
        ui.println(ConsoleStyle.cyan("╠══════════════════════════════════════════════════════════════╣"));
        
        int index = 1;
        for (SessionData session : sessions) {
            String statusIcon = getStatusIcon(session.getStatus());
            String shortId = session.getSessionId().substring(0, Math.min(10, session.getSessionId().length()));
            String time = session.getLastActiveAt().format(TIME_FORMAT);
            
            String preview = session.getLastUserMessage();
            if (preview != null && preview.length() > 35) {
                preview = preview.substring(0, 35) + "...";
            } else if (preview == null) {
                preview = "(无预览)";
            }
            
            ui.println(ConsoleStyle.cyan("║") + 
                ConsoleStyle.bold(String.format(" %2d.", index++)) +
                String.format(" %s %-10s | %s | %d条", 
                    statusIcon, shortId, time, session.getMessageCount()) +
                ConsoleStyle.cyan("                    ║"));
            
            ui.println(ConsoleStyle.cyan("║") + 
                String.format("      %s", preview) +
                ConsoleStyle.cyan("                                          ").substring(0, Math.max(0, 50 - preview.length())) + 
                ConsoleStyle.cyan("║"));
            
            String toolCalls = session.getLastToolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                String toolInfo = "工具: " + toolCalls;
                if (toolInfo.length() > 45) {
                    toolInfo = toolInfo.substring(0, 45) + "...";
                }
                ui.println(ConsoleStyle.cyan("║") + 
                    ConsoleStyle.yellow(String.format("      %s", toolInfo)) +
                    ConsoleStyle.cyan("                                          ").substring(0, Math.max(0, 50 - toolInfo.length())) + 
                    ConsoleStyle.cyan("║"));
            }
        }
        
        ui.println(ConsoleStyle.cyan("╚══════════════════════════════════════════════════════════════╝"));
        ui.println();
        ui.println(ConsoleStyle.gray("提示: 使用 'resume <序号>' 恢复会话，'delete-session <序号>' 删除会话"));
        ui.println();
        
        return CommandResult.continueExecution();
    }

    private CommandResult handleResumeCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        
        if (parts.length < 2) {
            List<SessionData> sessions = sessionStorage.listResumableSessions();
            if (sessions.isEmpty()) {
                ui.println(ConsoleStyle.yellow("没有可恢复的会话"));
                ui.println();
                return CommandResult.continueExecution();
            }
            
            SessionData latest = sessions.get(0);
            ui.println(ConsoleStyle.green("将恢复最近的会话: " + latest.getSessionId()));
            ui.println(ConsoleStyle.gray("  消息数: " + latest.getMessageCount() + " | 状态: " + latest.getStatus()));
            ui.println();
            return CommandResult.resumeSession(latest);
        }
        
        String arg = parts[1].trim();
        
        try {
            int index = Integer.parseInt(arg);
            List<SessionData> sessions = sessionStorage.listSessions();
            
            if (index < 1 || index > sessions.size()) {
                ui.println(ConsoleStyle.red("无效的会话序号: " + index));
                ui.println();
                return CommandResult.continueExecution();
            }
            
            SessionData session = sessions.get(index - 1);
            if (!session.canResume()) {
                ui.println(ConsoleStyle.yellow("该会话已完成，无法恢复"));
                ui.println();
                return CommandResult.continueExecution();
            }
            
            return CommandResult.resumeSession(session);
            
        } catch (NumberFormatException e) {
            SessionData session = sessionStorage.loadSession(arg).orElse(null);
            if (session == null) {
                ui.println(ConsoleStyle.red("找不到会话: " + arg));
                ui.println();
                return CommandResult.continueExecution();
            }
            
            if (!session.canResume()) {
                ui.println(ConsoleStyle.yellow("该会话已完成，无法恢复"));
                ui.println();
                return CommandResult.continueExecution();
            }
            
            return CommandResult.resumeSession(session);
        }
    }

    private CommandResult handleDeleteSessionCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        
        if (parts.length < 2) {
            ui.println(ConsoleStyle.yellow("用法: delete-session <序号或会话ID>"));
            ui.println();
            return CommandResult.continueExecution();
        }
        
        String arg = parts[1].trim();
        
        try {
            int index = Integer.parseInt(arg);
            List<SessionData> sessions = sessionStorage.listSessions();
            
            if (index < 1 || index > sessions.size()) {
                ui.println(ConsoleStyle.red("无效的会话序号: " + index));
                ui.println();
                return CommandResult.continueExecution();
            }
            
            SessionData session = sessions.get(index - 1);
            boolean deleted = sessionStorage.deleteSession(session.getSessionId());
            
            if (deleted) {
                ui.println(ConsoleStyle.green("会话已删除: " + session.getSessionId()));
            } else {
                ui.println(ConsoleStyle.red("删除会话失败"));
            }
            ui.println();
            return CommandResult.continueExecution();
            
        } catch (NumberFormatException e) {
            boolean deleted = sessionStorage.deleteSession(arg);
            if (deleted) {
                ui.println(ConsoleStyle.green("会话已删除: " + arg));
            } else {
                ui.println(ConsoleStyle.red("找不到会话: " + arg));
            }
            ui.println();
            return CommandResult.continueExecution();
        }
    }

    private String getStatusIcon(SessionData.Status status) {
        switch (status) {
            case ACTIVE:
                return ConsoleStyle.green("●");
            case INTERRUPTED:
                return ConsoleStyle.yellow("○");
            case COMPLETED:
                return ConsoleStyle.gray("✓");
            default:
                return "?";
        }
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
