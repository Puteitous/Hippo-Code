package com.example.agent.progress;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.core.di.ServiceLocator;

import java.util.concurrent.atomic.AtomicBoolean;

public class ToolCallCard {

    private static final int TERMINAL_WIDTH = 80;

    private final String toolName;
    private final String toolCallId;
    private final int index;
    private final int total;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String currentStatus = "";
    private volatile long startTime;
    private final AgentUi ui;
    private final SpinnerManager spinnerManager = SpinnerManager.getInstance();

    public ToolCallCard(String toolName, String toolCallId, int index, int total) {
        this.toolName = toolName;
        this.toolCallId = toolCallId;
        this.index = index;
        this.total = total;
        this.ui = ServiceLocator.get(AgentUi.class);
    }

    public void start() {
        if ("ask_user".equals(toolName)) {
            running.set(false);
            return;
        }

        running.set(true);
        startTime = System.currentTimeMillis();
        currentStatus = "执行中...";

        spinnerManager.registerCard(this);
    }

    public void pauseSpinner() {
    }

    public void resumeSpinner() {
    }

    public void updateStatus(String status) {
        this.currentStatus = status;
    }

    public void completeSuccess(String resultPreview) {
        complete("✅", ConsoleStyle::green, "成功", resultPreview);
    }

    public void completeFailure(String errorMessage) {
        complete("❌", ConsoleStyle::red, "失败", errorMessage);
    }

    private int getTerminalWidth() {
        if (ui != null) {
            try {
                com.example.agent.core.AgentContext context = ServiceLocator.get(com.example.agent.core.AgentContext.class);
                if (context != null && context.getTerminal() != null) {
                    return context.getTerminal().getWidth();
                }
            } catch (Exception e) {
                // 忽略异常，使用默认值
            }
        }
        return TERMINAL_WIDTH;
    }

    private void complete(String marker, java.util.function.Function<String, String> color, String status, String detail) {
        spinnerManager.unregisterCard(this);
        running.set(false);

        if (ui == null) {
            return;
        }

        String prefix = String.format("[%d/%d]", index + 1, total);
        String displayDetail = truncate(detail, 60);
        int terminalWidth = getTerminalWidth();

        StringBuilder line = new StringBuilder();
        line.append("\r");
        line.append(" ".repeat(terminalWidth));
        line.append("\r");
        line.append("  ");
        line.append(ConsoleStyle.gray(prefix));
        line.append(" ");
        line.append(marker);
        line.append(" ");
        line.append(ConsoleStyle.boldYellow(toolName));
        line.append(" ");
        line.append(color.apply(status));
        line.append(" ");
        line.append(ConsoleStyle.gray(getElapsedTime()));

        ui.println(line.toString());

        if (displayDetail != null && !displayDetail.isEmpty()) {
            ui.println("       └─ " + ConsoleStyle.dim(displayDetail));
        }
    }

    public String getToolName() {
        return toolName;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getElapsedTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < 1000) {
            return elapsed + "ms";
        }
        return String.format("%.1fs", elapsed / 1000.0);
    }

    public String gray(String text) {
        return ConsoleStyle.gray(text);
    }

    public String cyan(String text) {
        return ConsoleStyle.cyan(text);
    }

    public String boldYellow(String text) {
        return ConsoleStyle.boldYellow(text);
    }

    public String dim(String text) {
        return ConsoleStyle.dim(text);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace("\n", " ").replace("\r", " ").trim();
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        return singleLine.substring(0, maxLength) + "...";
    }
}
