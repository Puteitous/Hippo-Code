package com.example.agent.progress;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.core.di.ServiceLocator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ToolCallCard {

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int SPINNER_DELAY = 100;
    private static final int TERMINAL_WIDTH = 80;

    private final String toolName;
    private final String toolCallId;
    private final int index;
    private final int total;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger spinnerFrame = new AtomicInteger(0);
    private volatile Thread spinnerThread;
    private volatile String currentStatus = "";
    private volatile long startTime;
    private final AgentUi ui;

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

        spinnerThread = new Thread(this::runSpinner, "spinner-" + toolCallId);
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    private void runSpinner() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                String spinner = SPINNER_FRAMES[spinnerFrame.get() % SPINNER_FRAMES.length];
                renderRunning(spinner);
                spinnerFrame.incrementAndGet();
                Thread.sleep(SPINNER_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void renderRunning(String spinner) {
        if (!running.get()) {
            return;
        }
        
        String prefix = String.format("[%d/%d]", index + 1, total);
        String content = String.format("  %s %s %s [%s] %s",
                ConsoleStyle.gray(prefix),
                ConsoleStyle.cyan(spinner),
                ConsoleStyle.boldYellow(toolName),
                ConsoleStyle.dim(currentStatus),
                ConsoleStyle.gray(getElapsedTime()));

        clearAndPrint(content);
    }

    private void clearAndPrint(String content) {
        if (ui == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("\r");
        sb.append(" ".repeat(TERMINAL_WIDTH));
        sb.append("\r");
        sb.append(content);
        ui.print(sb.toString());
    }

    public void updateStatus(String status) {
        this.currentStatus = status;
    }

    public void completeSuccess(String resultPreview) {
        stopSpinner();
        renderFinal("✅", ConsoleStyle::green, "成功", resultPreview);
    }

    public void completeFailure(String errorMessage) {
        stopSpinner();
        renderFinal("❌", ConsoleStyle::red, "失败", errorMessage);
    }

    private void stopSpinner() {
        running.set(false);
        if (spinnerThread != null) {
            spinnerThread.interrupt();
            try {
                spinnerThread.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            spinnerThread = null;
        }
        clearLine();
    }

    private void clearLine() {
        if (ui == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("\r");
        sb.append(" ".repeat(TERMINAL_WIDTH));
        sb.append("\r");
        ui.print(sb.toString());
    }

    private void renderFinal(String marker, java.util.function.Function<String, String> color, String status, String detail) {
        if (ui == null) {
            return;
        }
        
        String prefix = String.format("[%d/%d]", index + 1, total);
        String displayDetail = truncate(detail, 60);

        StringBuilder line = new StringBuilder();
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

    private String getElapsedTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < 1000) {
            return elapsed + "ms";
        }
        return String.format("%.1fs", elapsed / 1000.0);
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
