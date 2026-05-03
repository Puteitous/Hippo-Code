package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.core.AgentContext;
import com.example.agent.core.AgentMode;
import com.example.agent.domain.truncation.TruncationService;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.progress.ToolCardRenderer;
import com.example.agent.application.ConversationService;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.orchestrator.ToolOrchestrator;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.tools.concurrent.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolCallProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallProcessor.class);

    private final ConcurrentToolExecutor concurrentToolExecutor;
    private final ToolOrchestrator toolOrchestrator;
    private final ConversationService conversationService;
    private final Conversation conversation;
    private final AgentUi ui;
    private final TruncationService truncationService;
    private final AgentContext context;

    public ToolCallProcessor(AgentContext context,
                             ConcurrentToolExecutor concurrentToolExecutor,
                             ConversationService conversationService,
                             Conversation conversation,
                             AgentUi ui) {
        this.context = context;
        this.concurrentToolExecutor = concurrentToolExecutor;
        this.toolOrchestrator = context.getToolOrchestrator();
        this.conversationService = conversationService;
        this.conversation = conversation;
        this.ui = ui;
        this.truncationService = new TruncationService(TokenEstimatorFactory.getDefault());
    }

    public void processToolCallsConcurrently(List<ToolCall> toolCalls, ConversationLogger conversationLogger) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        AgentMode mode = context.getCurrentMode();

        List<ToolCall> validToolCalls = new ArrayList<>();
        Map<String, String> argumentsMap = new HashMap<>();

        for (ToolCall toolCall : toolCalls) {
            if (toolCall.getFunction() != null && toolCall.getFunction().getName() != null
                    && !toolCall.getFunction().getName().isEmpty()) {
                
                String toolName = toolCall.getFunction().getName();
                if (!mode.isToolAllowed(toolName)) {
                    String msg = String.format(
                        "[%s] 模式下不允许使用工具 '%s'，请输入 /builder 切换到构建模式",
                        mode.getDisplayName(), toolName
                    );
                    ui.println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.yellow(msg));
                    logger.warn("模式权限拦截: {} - {}", mode, toolName);
                    conversationService.addToolResult(conversation, toolCall.getId(), toolName, "权限受限: " + msg);
                    continue;
                }
                
                validToolCalls.add(toolCall);
                argumentsMap.put(toolCall.getId(), toolCall.getFunction().getArguments());
            } else {
                ui.println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.red("跳过无效工具调用: 工具名称为空"));
            }
        }

        if (validToolCalls.isEmpty()) {
            return;
        }

        ToolCardRenderer renderer = new ToolCardRenderer(ui);
        concurrentToolExecutor.registerCallback(renderer);
        
        renderer.renderHeader();

        if (toolOrchestrator != null && toolOrchestrator.isEnabled() && validToolCalls.size() > 1) {
            ui.println(ConsoleStyle.gray("  │"));
            ui.println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.cyan("🔗 使用编排引擎分析依赖关系..."));
        }

        List<ToolExecutionResult> results = (toolOrchestrator != null && toolOrchestrator.isEnabled())
                ? toolOrchestrator.executeConcurrently(validToolCalls)
                : concurrentToolExecutor.executeConcurrently(validToolCalls);

        concurrentToolExecutor.removeCallback(renderer);

        com.example.agent.progress.SpinnerManager.getInstance().clear();

        long totalTime = System.currentTimeMillis() - startTime;
        
        renderer.renderFooter(validToolCalls.size(), totalTime);

        for (ToolExecutionResult result : results) {
            String arguments = argumentsMap.get(result.getToolCallId());
            String rawResult = result.getResult() != null ? result.getResult() : "";
            String truncatedResult = truncationService.truncateToolOutput(result.getToolName(), rawResult);

            if (result.isSuccess()) {
                if (conversationLogger != null) {
                    conversationLogger.logToolCall(
                            result.getToolName(),
                            arguments != null ? arguments : "{}",
                            truncatedResult,
                            result.getExecutionTimeMs(),
                            true
                    );
                }

                conversationService.addToolResult(conversation, result.getToolCallId(), result.getToolName(), truncatedResult);
            } else {
                String errorMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "未知错误";

                if (conversationLogger != null) {
                    conversationLogger.logToolCall(
                            result.getToolName(),
                            arguments != null ? arguments : "{}",
                            errorMsg,
                            result.getExecutionTimeMs(),
                            false
                    );
                }

                String errorResult = "Error: " + errorMsg + "\nPlease try a different approach or check if the path is correct.";
                conversationService.addToolResult(conversation, result.getToolCallId(), result.getToolName(), errorResult);
            }
        }
    }
}
