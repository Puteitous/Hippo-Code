package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.console.InputHandler;
import com.example.agent.core.AgentContext;
import com.example.agent.intent.IntentRecognizer;
import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.logging.LogDirectoryManager;
import com.example.agent.plan.ExecutionContext;
import com.example.agent.plan.ExecutionPlan;
import com.example.agent.plan.PlanningContext;
import com.example.agent.plan.TaskPlanner;
import com.example.agent.plan.PlanExecutor;
import com.example.agent.plan.PlanResult;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionData;
import com.example.agent.session.SessionStorage;
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;

public class ConversationLoop {

    private static final Logger logger = LoggerFactory.getLogger(ConversationLoop.class);
    private static final int MAX_EMPTY_RESPONSE_RETRIES = 3;

    private final AgentContext context;
    private final AgentTurnExecutor turnExecutor;
    private final ConversationManager conversationManager;
    private final TokenEstimator tokenEstimator;
    private final InputHandler inputHandler;
    private final AgentUi ui;
    private final IntentRecognizer intentRecognizer;
    private final TaskPlanner taskPlanner;
    private final PlanExecutor planExecutor;
    private final SessionStorage sessionStorage;

    private int conversationRound = 0;
    private String currentConversationId;
    private ConversationLogger conversationLogger;
    private IntentResult lastIntentResult;
    private ExecutionPlan lastExecutionPlan;
    private PlanResult lastPlanResult;

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui) {
        this(context, turnExecutor, inputHandler, ui, null, null, null, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer) {
        this(context, turnExecutor, inputHandler, ui, intentRecognizer, null, null, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer,
                            TaskPlanner taskPlanner) {
        this(context, turnExecutor, inputHandler, ui, intentRecognizer, taskPlanner, null, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer,
                            TaskPlanner taskPlanner,
                            PlanExecutor planExecutor) {
        this(context, turnExecutor, inputHandler, ui, intentRecognizer, taskPlanner, planExecutor, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer,
                            TaskPlanner taskPlanner,
                            PlanExecutor planExecutor,
                            SessionStorage sessionStorage) {
        this.context = context;
        this.turnExecutor = turnExecutor;
        this.conversationManager = context.getConversationManager();
        this.tokenEstimator = context.getTokenEstimator();
        this.inputHandler = inputHandler;
        this.ui = ui;
        this.intentRecognizer = intentRecognizer;
        this.taskPlanner = taskPlanner;
        this.planExecutor = planExecutor;
        this.sessionStorage = sessionStorage != null ? sessionStorage : new SessionStorage();
    }

    public void processUserInput(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return;
        }

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

        if (intentRecognizer != null && intentRecognizer.isEnabled()) {
            lastIntentResult = recognizeIntent(userInput);
            displayIntentInfo(lastIntentResult);
        }

        if (taskPlanner != null && taskPlanner.isEnabled() && lastIntentResult != null) {
            lastExecutionPlan = createExecutionPlan(userInput, lastIntentResult);
            displayPlanInfo(lastExecutionPlan);
        }

        conversationManager.addUserMessage(userInput);
        conversationManager.trimHistory((messageCount, tokenCount) -> {
            ui.println(ConsoleStyle.gray("  [历史已精简: ") + ConsoleStyle.yellow(String.valueOf(messageCount)) + ConsoleStyle.gray(" 条消息, 约 ") + ConsoleStyle.yellow(String.valueOf(tokenCount)) + ConsoleStyle.gray(" tokens]") );
            ui.println();
        });

        ui.println();
        ui.println(ConsoleStyle.conversationDivider(conversationRound));
        ui.println();
        ui.println(ConsoleStyle.userLabel() + ": " + ConsoleStyle.white(userInput));
        ui.println();

        processAgentLoop();
    }

    private IntentResult recognizeIntent(String userInput) {
        try {
            IntentResult result = intentRecognizer.recognize(userInput, conversationManager.getHistory());
            logger.info("意图识别结果: {}", result);
            return result;
        } catch (Exception e) {
            logger.warn("意图识别失败: {}", e.getMessage());
            return IntentResult.unknown();
        }
    }

    private ExecutionPlan createExecutionPlan(String userInput, IntentResult intent) {
        try {
            PlanningContext planningContext = PlanningContext.builder()
                    .userInput(userInput)
                    .conversationHistory(conversationManager.getHistory())
                    .conversationManager(conversationManager)
                    .currentRound(conversationRound)
                    .build();

            ExecutionPlan plan = taskPlanner.plan(intent, planningContext);
            logger.info("执行计划: {}", plan);
            return plan;
        } catch (Exception e) {
            logger.warn("规划失败: {}", e.getMessage());
            return ExecutionPlan.empty(intent);
        }
    }

    private void displayIntentInfo(IntentResult intent) {
        if (intent == null || intent.getType() == IntentType.UNKNOWN) {
            return;
        }

        ui.println(ConsoleStyle.dim("  [意图: " + intent.getType().getDisplayName() + 
                " | 置信度: " + String.format("%.0f%%", intent.getConfidence() * 100) + "]"));
        ui.println();
    }

    private void displayPlanInfo(ExecutionPlan plan) {
        if (plan == null || plan.isEmpty()) {
            return;
        }

        ui.println(ConsoleStyle.dim("  [计划: " + plan.getStepCount() + " 个步骤 | 策略: " + 
                plan.getStrategy().getDisplayName() + "]"));
        ui.println();
    }

    private void processAgentLoop() {
        int emptyResponseRetries = 0;
        boolean completed = false;

        try {
            while (!turnExecutor.isInterrupted()) {
                try {
                    AgentTurnResult result = turnExecutor.execute(conversationLogger, currentConversationId);

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
            SessionData sessionData = conversationManager.exportSession(currentConversationId, status);
            SessionData saved = sessionStorage.saveSession(sessionData);
            
            if (saved != null) {
                logger.debug("会话已保存: {}, 状态: {}", currentConversationId, status);
            } else {
                ui.println();
                ui.println(ConsoleStyle.yellow("  ⚠ 会话保存失败，历史记录可能丢失"));
                ui.println(ConsoleStyle.gray("    提示: 请检查磁盘空间或会话存储目录权限"));
                ui.println();
                logger.warn("会话保存失败: {}", currentConversationId);
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

    public String getCurrentConversationId() {
        return currentConversationId;
    }

    public IntentResult getLastIntentResult() {
        return lastIntentResult;
    }

    public ExecutionPlan getLastExecutionPlan() {
        return lastExecutionPlan;
    }

    public PlanResult getLastPlanResult() {
        return lastPlanResult;
    }

    public SessionStorage getSessionStorage() {
        return sessionStorage;
    }

    public void resumeSession(SessionData session) {
        if (session == null) {
            return;
        }
        
        conversationManager.importSession(session);
        conversationManager.fixUnfinishedToolCall();
        
        currentConversationId = session.getSessionId();
        conversationRound = countUserMessages(session.getMessages());
        
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
