package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.domain.truncation.TruncationService;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.progress.ToolCardRenderer;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.tools.concurrent.ToolExecutionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolCallProcessor {

    private final ConcurrentToolExecutor concurrentToolExecutor;
    private final ConversationManager conversationManager;
    private final AgentUi ui;
    private final TruncationService truncationService;

    public ToolCallProcessor(ConcurrentToolExecutor concurrentToolExecutor,
                             ConversationManager conversationManager,
                             AgentUi ui) {
        this.concurrentToolExecutor = concurrentToolExecutor;
        this.conversationManager = conversationManager;
        this.ui = ui;
        this.truncationService = new TruncationService(TokenEstimatorFactory.getDefault());
    }

    public void processToolCallsConcurrently(List<ToolCall> toolCalls, ConversationLogger conversationLogger) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();

        List<ToolCall> validToolCalls = new ArrayList<>();
        Map<String, String> argumentsMap = new HashMap<>();

        for (ToolCall toolCall : toolCalls) {
            if (toolCall.getFunction() != null && toolCall.getFunction().getName() != null
                    && !toolCall.getFunction().getName().isEmpty()) {
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

        List<ToolExecutionResult> results = concurrentToolExecutor.executeConcurrently(validToolCalls);

        concurrentToolExecutor.removeCallback(renderer);

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

                conversationManager.addToolResult(result.getToolCallId(), result.getToolName(), truncatedResult);
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
                conversationManager.addToolResult(result.getToolCallId(), result.getToolName(), errorResult);
            }
        }
    }
}
