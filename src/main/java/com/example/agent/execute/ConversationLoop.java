package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.console.InputHandler;
import com.example.agent.core.AgentContext;
import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.logging.WorkspaceManager;
import com.example.agent.service.ConversationManager;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionStorage;
import com.example.agent.session.TranscriptLoader;
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

public class ConversationLoop {

    private static final Logger logger = LoggerFactory.getLogger(ConversationLoop.class);
    private static final int MAX_EMPTY_RESPONSE_RETRIES = 3;

    private final AgentContext context;
    private final AgentTurnExecutor turnExecutor;
    private final ConversationManager conversationManager;
    private final TokenEstimator tokenEstimator;
    private final InputHandler inputHandler;
    private final AgentUi ui;
    private final SessionStorage sessionStorage;

    private int conversationRound = 1;
    private String currentSessionId;
    private ConversationLogger conversationLogger;
    private volatile boolean processing = false;

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui) {
        this(context, turnExecutor, inputHandler, ui, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui,
                            SessionStorage sessionStorage) {
        this.context = context;
        this.turnExecutor = turnExecutor;
        this.conversationManager = context.getConversationManager();
        this.tokenEstimator = context.getTokenEstimator();
        this.inputHandler = inputHandler;
        this.ui = ui;
        this.sessionStorage = sessionStorage != null ? sessionStorage : new SessionStorage();
    }

    private void ensureConversationInitialized() {
        boolean managerWasReset = conversationManager.getMessageCount() == 1 
                && conversationManager.getHistory().get(0).isSystem();
        
        if (currentSessionId == null || conversationLogger == null || managerWasReset) {
            startNewConversation();
        }
    }

    public void startNewConversation() {
        String sessionId = String.valueOf(System.currentTimeMillis());
        currentSessionId = sessionId;
        conversationRound = 1;
        conversationManager.reset();
        context.getContextManager().clear();

        conversationManager.setMessageSyncListener(
            msg -> context.getContextManager().addMessage(msg)
        );

        MDC.put("sessionId", sessionId.substring(0, Math.min(12, sessionId.length())));
        Path logFile = WorkspaceManager.getSessionLogFile(
            WorkspaceManager.getCurrentProjectKey(), sessionId
        );
        conversationLogger = new ConversationLogger(sessionId, logFile);
        conversationManager.enableTranscript(sessionId);
        logger.info("新会话已启动: {}", sessionId);
    }

    public void processUserInput(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return;
        }

        processing = true;
        turnExecutor.setInterrupted(false);

        ensureConversationInitialized();
        MDC.put("turn", String.valueOf(conversationRound));

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

        // ✅ 新架构：ContextManager 上下文压缩自动处理
        //    旧的 trimHistory 已废弃，原因：
        //    1. 直接删除 ConversationManager（真相源）破坏 SSOT 原则
        //    2. 现在通过 AutoCompactTrigger 三级阈值自动触发压缩
        //    3. 90% → ContextClipper 零成本裁剪，95% → LLM 深度摘要
        //    4. 压缩仅在 ContextWindow（工作窗口）执行，真相源永不删除
        // conversationManager.trimHistory((messageCount, tokenCount) -> {
        //     ui.println(ConsoleStyle.gray("  [历史已精简: ") + ConsoleStyle.yellow(String.valueOf(messageCount)) + ConsoleStyle.gray(" 条消息, 约 ") + ConsoleStyle.yellow(String.valueOf(tokenCount)) + ConsoleStyle.gray(" tokens]") );
        //     ui.println();
        // });

        ui.println();
        ui.println(ConsoleStyle.conversationDivider(conversationRound));
        ui.println();
        ui.println(ConsoleStyle.userLabel() + ": " + ConsoleStyle.white(userInput));
        ui.println();

        try {

            processAgentLoop();

        } finally {
            conversationRound++;
            processing = false;
            MDC.clear();
        }
    }

    public boolean isProcessing() {
        return processing;
    }

    private void processAgentLoop() {
        int emptyResponseRetries = 0;
        boolean completed = false;

        try {
            while (!turnExecutor.isInterrupted()) {
                try {
                    AgentTurnResult result = turnExecutor.execute(conversationLogger, currentSessionId);

                    if (result == AgentTurnResult.EMPTY_RESPONSE) {
                        emptyResponseRetries++;
                        if (emptyResponseRetries < MAX_EMPTY_RESPONSE_RETRIES) {
                            ui.println(ConsoleStyle.gray("  │"));
                            ui.println(ConsoleStyle.yellow("  │  检测到空响应，正在重试 (" + emptyResponseRetries + "/" + MAX_EMPTY_RESPONSE_RETRIES + ")..."));
                            ui.println(ConsoleStyle.gray("  │"));
                            continue;
                        } else {
                            ui.println(ConsoleStyle.gray("  │"));
                            ui.println(ConsoleStyle.yellow("  └─ AI 多次返回空响应，请尝试重新描述您的需求。"));
                            ui.println();
                            break;
                        }
                    }

                    emptyResponseRetries = 0;

                    if (result == null || result == AgentTurnResult.DONE || result == AgentTurnResult.ERROR) {
                        completed = (result == AgentTurnResult.DONE);
                        break;
                    }

                } catch (LlmException e) {
                    handleApiError(e);
                    break;
                } catch (RuntimeException e) {
                    if ("Interrupted".equals(e.getMessage())) {
                        throw new UserInterruptException("User interrupted");
                    }
                    ui.println();
                    ui.println(ConsoleStyle.gray("  │"));
                    ui.println(ConsoleStyle.gray("  └─ ") + ConsoleStyle.red("处理错误: " + e.getMessage()));
                    e.printStackTrace();
                    break;
                } catch (Exception e) {
                    ui.println();
                    ui.println(ConsoleStyle.gray("  │"));
                    ui.println(ConsoleStyle.gray("  └─ ") + ConsoleStyle.red("处理错误: " + e.getMessage()));
                    e.printStackTrace();
                    break;
                }
            }
        } finally {
            saveSession(completed);
            
            if (conversationLogger != null) {
                if (completed) {
                    conversationLogger.logSummary();
                } else {
                    conversationLogger.logInterruptedSummary();
                }
            }
        }
    }

    private void saveSession(boolean completed) {
        if (sessionStorage == null) {
            return;
        }
        
        if (context == null || context.getConfig() == null || 
            context.getConfig().getSession() == null ||
            !context.getConfig().getSession().isPersistSessions()) {
            return;
        }
        
        try {
            SessionData.Status status = completed ? SessionData.Status.COMPLETED : SessionData.Status.INTERRUPTED;
            SessionData sessionData = conversationManager.exportSession(currentSessionId, status);
            SessionData saved = sessionStorage.saveSession(sessionData);
            
            if (saved != null) {
                logger.debug("会话已保存: {}, 状态: {}", currentSessionId, status);
            } else {
                ui.println();
                ui.println(ConsoleStyle.yellow("  ⚠ 会话保存失败，历史记录可能丢失"));
                ui.println(ConsoleStyle.gray("    提示: 请检查磁盘空间或会话存储目录权限"));
                ui.println();
                logger.warn("会话保存失败: {}", currentSessionId);
            }
        } catch (Exception e) {
            logger.warn("保存会话失败: {}", e.getMessage());
            ui.println();
            ui.println(ConsoleStyle.yellow("  ⚠ 会话保存失败: " + e.getMessage()));
            ui.println(ConsoleStyle.gray("    提示: 请检查磁盘空间或会话存储目录权限"));
            ui.println();
        }
    }

    private void handleApiError(LlmException e) throws UserInterruptException {
        String message = e.getMessage();
        if (message != null && message.contains("Interrupted")) {
            ui.println();
            ui.println(ConsoleStyle.yellow("用户已终止对话"));
            ui.println();
            throw new UserInterruptException("User interrupted");
        }

        ui.println();
        ui.println(ConsoleStyle.red("╔══════════════════════════════════════════════════╗"));
        ui.println(ConsoleStyle.red("║                  API 调用失败                    ║"));
        ui.println(ConsoleStyle.red("╚══════════════════════════════════════════════════╝"));
        ui.println();
        ui.println(ConsoleStyle.red("错误信息: " + e.getMessage()));
        ui.println();

        if (isRetryableError(e)) {
            ui.println(ConsoleStyle.yellow("您可以:"));
            ui.println(ConsoleStyle.gray("  1. 输入 'retry' 重试"));
            ui.println(ConsoleStyle.gray("  2. 输入 'reset' 重置会话"));
            ui.println(ConsoleStyle.gray("  3. 输入 'config' 检查配置"));
            ui.println(ConsoleStyle.gray("  4. 继续输入其他内容"));
            ui.println();
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

    public void interrupt() {
        turnExecutor.setInterrupted(true);
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public SessionStorage getSessionStorage() {
        return sessionStorage;
    }

    public void resumeSession(SessionData session) {
        if (session == null) {
            return;
        }
        
        String sessionId = session.getSessionId();
        
        boolean loadedFromTranscript = TranscriptLoader.loadToConversationManager(
            sessionId, conversationManager
        );
        
        if (!loadedFromTranscript) {
            conversationManager.importSession(session);
        }
        
        conversationManager.fixUnfinishedToolCall();
        
        currentSessionId = sessionId;
        conversationRound = countUserMessages(conversationManager.getHistory());
        
        Path logFile = WorkspaceManager.getSessionLogFile(
            WorkspaceManager.getCurrentProjectKey(), sessionId
        );
        conversationLogger = new ConversationLogger(sessionId, logFile);
        conversationManager.enableTranscript(sessionId);
        MDC.put("sessionId", sessionId.substring(0, Math.min(12, sessionId.length())));
        
        logger.info("会话已恢复: {}, 轮次: {}", sessionId, conversationRound);
        
        String shortId = session.getSessionId().substring(0, Math.min(12, session.getSessionId().length()));
        String time = session.getLastActiveAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        
        ui.println();
        ui.println(ConsoleStyle.green("╔══════════════════════════════════════════════════════════════╗"));
        ui.println(ConsoleStyle.green("║                    ✓ 会话已恢复                              ║"));
        ui.println(ConsoleStyle.green("╠══════════════════════════════════════════════════════════════╣"));
        ui.println(ConsoleStyle.green("║") + 
            String.format("  会话: %-52s", shortId) + 
            ConsoleStyle.green("║"));
        ui.println(ConsoleStyle.green("║") + 
            String.format("  时间: %-52s", time) + 
            ConsoleStyle.green("║"));
        ui.println(ConsoleStyle.green("║") + 
            String.format("  消息: %-52d", session.getMessageCount()) + 
            ConsoleStyle.green("║"));
        
        String toolCalls = session.getLastToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            ui.println(ConsoleStyle.green("║") + 
                String.format("  工具: %-52s", toolCalls) + 
                ConsoleStyle.green("║"));
        }
        
        ui.println(ConsoleStyle.green("╚══════════════════════════════════════════════════════════════╝"));
        ui.println();
        ui.println(ConsoleStyle.gray("提示: 您可以继续之前的对话，或输入 'reset' 开始新会话"));
        ui.println();
    }

    private int countUserMessages(java.util.List<com.example.agent.llm.model.Message> messages) {
        if (messages == null) {
            return 0;
        }
        return (int) messages.stream()
            .filter(m -> "user".equals(m.getRole()))
            .count();
    }
}
