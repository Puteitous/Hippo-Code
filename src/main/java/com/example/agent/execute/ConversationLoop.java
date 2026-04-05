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
import com.example.agent.logging.LogDirectoryManager;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;

public class ConversationLoop {

    private static final Logger logger = LoggerFactory.getLogger(ConversationLoop.class);
    private static final int MAX_EMPTY_RESPONSE_RETRIES = 2;

    private final AgentContext context;
    private final AgentTurnExecutor turnExecutor;
    private final ConversationManager conversationManager;
    private final TokenEstimator tokenEstimator;
    private final InputHandler inputHandler;
    private final AgentUi ui;

    private int conversationRound = 0;
    private String currentConversationId;
    private ConversationLogger conversationLogger;

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui) {
        this.context = context;
        this.turnExecutor = turnExecutor;
        this.conversationManager = context.getConversationManager();
        this.tokenEstimator = context.getTokenEstimator();
        this.inputHandler = inputHandler;
        this.ui = ui;
    }

    public void processUserInput(String userInput) {
        turnExecutor.setInterrupted(false);
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
            ui.println(ConsoleStyle.gray("  [历史已精简: ") + ConsoleStyle.yellow(String.valueOf(messageCount)) + ConsoleStyle.gray(" 条消息, 约 ") + ConsoleStyle.yellow(String.valueOf(tokenCount)) + ConsoleStyle.gray(" tokens]"));
            ui.println();
        });

        ui.println();
        ui.println(ConsoleStyle.conversationDivider(conversationRound));
        ui.println();
        ui.println(ConsoleStyle.userLabel() + ": " + ConsoleStyle.white(userInput));
        ui.println();

        processAgentLoop();
    }

    private void processAgentLoop() {
        int emptyResponseRetries = 0;

        while (!turnExecutor.isInterrupted()) {
            try {
                AgentTurnResult result = turnExecutor.execute(conversationLogger);

                if (result == AgentTurnResult.EMPTY_RESPONSE) {
                    emptyResponseRetries++;
                    if (emptyResponseRetries <= MAX_EMPTY_RESPONSE_RETRIES) {
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

                if (result == AgentTurnResult.DONE || result == AgentTurnResult.ERROR) {
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

    public String getCurrentConversationId() {
        return currentConversationId;
    }
}
