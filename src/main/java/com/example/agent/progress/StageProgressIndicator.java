package com.example.agent.progress;

import com.example.agent.console.ConsoleStyle;

public class StageProgressIndicator {

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int SPINNER_DELAY = 120;
    private static final int TERMINAL_WIDTH = 80;

    private volatile boolean running = false;
    private Thread spinnerThread;
    private int frame = 0;

    public void startToolExecution() {
        if (running) {
            return;
        }
        running = true;

        spinnerThread = new Thread(this::runSpinner, "tool-progress");
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    public void stop() {
        running = false;
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

    private void runSpinner() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                renderRunning();
                frame = (frame + 1) % SPINNER_FRAMES.length;
                Thread.sleep(SPINNER_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void renderRunning() {
        String spinner = SPINNER_FRAMES[frame];
        String line = "\r\033[K  " + ConsoleStyle.cyan(spinner) + " " + 
                ConsoleStyle.boldYellow("工具执行中...") +
                ConsoleStyle.gray(" 请稍候");
        
        System.out.print(line);
    }

    private void clearLine() {
        StringBuilder sb = new StringBuilder("\r");
        sb.append(" ".repeat(TERMINAL_WIDTH));
        sb.append("\r");
        System.out.print(sb);
    }
}
