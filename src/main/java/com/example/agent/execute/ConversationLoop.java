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
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

    private int conversationRound = 0;
    private String currentConversationId;
    private ConversationLogger conversationLogger;
    private IntentResult lastIntentResult;
    private ExecutionPlan lastExecutionPlan;
    private PlanResult lastPlanResult;

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui) {
        this(context, turnExecutor, inputHandler, ui, null, null, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer) {
        this(context, turnExecutor, inputHandler, ui, intentRecognizer, null, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer,
                            TaskPlanner taskPlanner) {
        this(context, turnExecutor, inputHandler, ui, intentRecognizer, taskPlanner, null);
    }

    public ConversationLoop(AgentContext context, AgentTurnExecutor turnExecutor,
                            InputHandler inputHandler, AgentUi ui, 
                            IntentRecognizer intentRecognizer,
                            TaskPlanner taskPlanner,
                            PlanExecutor planExecutor) {
        this.context = context;
        this.turnExecutor = turnExecutor;
        this.conversationManager = context.getConversationManager();
        this.tokenEstimator = context.getTokenEstimator();
        this.inputHandler = inputHandler;
        this.ui = ui;
        this.intentRecognizer = intentRecognizer;
        this.taskPlanner = taskPlanner;
        this.planExecutor = planExecutor;
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
            if (conversationLogger != null) {
                if (completed) {
                    conversationLogger.logSummary();
                } else {
                    conversationLogger.logInterruptedSummary();
                }
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

    public IntentResult getLastIntentResult() {
        return lastIntentResult;
    }

    public ExecutionPlan getLastExecutionPlan() {
        return lastExecutionPlan;
    }

    public PlanResult getLastPlanResult() {
        return lastPlanResult;
    }
}
