package com.example.agent.progress;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.tools.concurrent.ToolExecutionResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolCardRenderer implements ToolExecutionCallback {

    private final AgentUi ui;
    private final Map<String, ToolCallCard> cards = new ConcurrentHashMap<>();

    public ToolCardRenderer(AgentUi ui) {
        this.ui = ui;
    }

    @Override
    public void onToolStart(ToolCall toolCall, int index, int total) {
        String toolName = toolCall.getFunction() != null ? toolCall.getFunction().getName() : "unknown";
        ToolCallCard card = new ToolCallCard(toolName, toolCall.getId(), index, total);
        cards.put(toolCall.getId(), card);
        card.start();
    }

    @Override
    public void onToolComplete(ToolCall toolCall, ToolExecutionResult result, int index, int total) {
        ToolCallCard card = cards.remove(toolCall.getId());
        if (card == null) {
            return;
        }

        if (result.isSuccess()) {
            card.completeSuccess(result.getResult());
        } else {
            card.completeFailure(result.getErrorMessage());
        }
    }

    public void renderHeader() {
        ui.println();
        ui.println(ConsoleStyle.gray("  │"));
        ui.println(ConsoleStyle.gray("  ├─ ") + ConsoleStyle.boldYellow("工具执行中..."));
        ui.println(ConsoleStyle.gray("  │"));
    }

    public void renderFooter(int totalTools, long totalTimeMs) {
        if (totalTools > 1) {
            ui.println(ConsoleStyle.gray("  │  ") + ConsoleStyle.dim(
                    String.format("并发执行 %d 个工具，总耗时 %d ms", totalTools, totalTimeMs)));
        }
    }
}
