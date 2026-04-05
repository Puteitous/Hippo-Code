package com.example.agent.execute;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.logging.ConversationLogger;
import com.example.agent.service.ConversationManager;
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

    public ToolCallProcessor(ConcurrentToolExecutor concurrentToolExecutor,
                             ConversationManager conversationManager,
                             AgentUi ui) {
        this.concurrentToolExecutor = concurrentToolExecutor;
        this.conversationManager = conversationManager;
        this.ui = ui;
    }

    public void processToolCallsConcurrently(List<ToolCall> toolCalls, ConversationLogger conversationLogger) {
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

        List<ToolExecutionResult> results = concurrentToolExecutor.executeConcurrently(validToolCalls);

        long totalTime = System.currentTimeMillis() - startTime;

        for (ToolExecutionResult result : results) {
            String arguments = argumentsMap.get(result.getToolCallId());

            if (result.isSuccess()) {
                ui.println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.toolCall(result.getToolName(), "成功"));
                String displayResult = ui.truncate(result.getResult(), 100);
                ui.println(ConsoleStyle.gray("  │  └─ ") + ConsoleStyle.dim(displayResult));

                if (conversationLogger != null) {
                    conversationLogger.logToolCall(
                            result.getToolName(),
                            arguments != null ? arguments : "{}",
                            result.getResult(),
                            result.getExecutionTimeMs(),
                            true
                    );
                }

                conversationManager.addToolResult(result.getToolCallId(), result.getToolName(), result.getResult());
            } else {
                ui.println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.toolCall(result.getToolName(), "失败"));
                ui.println(ConsoleStyle.gray("  │  └─ ") + ConsoleStyle.red(result.getErrorMessage()));

                if (conversationLogger != null) {
                    conversationLogger.logToolCall(
                            result.getToolName(),
                            arguments != null ? arguments : "{}",
                            result.getErrorMessage(),
                            result.getExecutionTimeMs(),
                            false
                    );
                }

                String errorResult = "Error: " + result.getErrorMessage() + "\nPlease try a different approach or check if the path is correct.";
                conversationManager.addToolResult(result.getToolCallId(), result.getToolName(), errorResult);
            }
        }

        if (toolCalls.size() > 1) {
            ui.println(ConsoleStyle.gray("  │"));
            ui.println(ConsoleStyle.gray("  │  ") + ConsoleStyle.dim(
                    String.format("并发执行 %d 个工具，总耗时 %d ms", toolCalls.size(), totalTime)));
        }
    }
}
