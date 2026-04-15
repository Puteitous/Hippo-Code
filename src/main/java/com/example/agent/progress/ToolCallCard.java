package com.example.agent.progress;

import com.example.agent.console.ConsoleStyle;

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

    public ToolCallCard(String toolName, String toolCallId, int index, int total) {
        this.toolName = toolName;
        this.toolCallId = toolCallId;
        this.index = index;
        this.total = total;
    }

    public void start() {
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
        StringBuilder sb = new StringBuilder("\r");
        sb.append(" ".repeat(TERMINAL_WIDTH));
        sb.append("\r");
        sb.append(content);
        System.out.print(sb);
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
        }
        clearLine();
    }

    private void clearLine() {
        StringBuilder sb = new StringBuilder("\r");
        sb.append(" ".repeat(TERMINAL_WIDTH));
        sb.append("\r");
        System.out.print(sb);
    }

    private void renderFinal(String marker, java.util.function.Function<String, String> color, String status, String detail) {
        String prefix = String.format("[%d/%d]", index + 1, total);
        String displayDetail = truncate(detail, 60);

        System.out.print("  ");
        System.out.print(ConsoleStyle.gray(prefix));
        System.out.print(" ");
        System.out.print(marker);
        System.out.print(" ");
        System.out.print(ConsoleStyle.boldYellow(toolName));
        System.out.print(" ");
        System.out.print(color.apply(status));
        System.out.print(" ");
        System.out.print(ConsoleStyle.gray(getElapsedTime()));
        System.out.println();

        if (displayDetail != null && !displayDetail.isEmpty()) {
            System.out.println("       └─ " + ConsoleStyle.dim(displayDetail));
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
