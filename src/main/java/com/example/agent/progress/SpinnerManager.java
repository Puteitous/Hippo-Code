package com.example.agent.progress;

import com.example.agent.console.AgentUi;
import com.example.agent.core.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

public class SpinnerManager {

    private static final SpinnerManager INSTANCE = new SpinnerManager();
    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int SPINNER_DELAY = 100;
    private static final int TERMINAL_WIDTH = 80;

    private final List<ToolCallCard> activeCards = new ArrayList<>();
    private boolean paused = false;
    private Thread renderThread;
    private final Object renderLock = new Object();
    private int spinnerFrame = 0;
    private final AgentUi ui;

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

    private SpinnerManager() {
        this.ui = ServiceLocator.get(AgentUi.class);
    }

    public static SpinnerManager getInstance() {
        return INSTANCE;
    }

    public void registerCard(ToolCallCard card) {
        synchronized (renderLock) {
            activeCards.add(card);
            ensureRenderThreadRunning();
        }
    }

    public void unregisterCard(ToolCallCard card) {
        synchronized (renderLock) {
            activeCards.remove(card);
            if (activeCards.isEmpty() && renderThread != null) {
                renderThread.interrupt();
                renderThread = null;
            }
        }
    }

    private void ensureRenderThreadRunning() {
        if (renderThread == null || !renderThread.isAlive()) {
            renderThread = new Thread(this::runRenderLoop, "spinner-manager");
            renderThread.setDaemon(true);
            renderThread.start();
        }
    }

    private void runRenderLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (renderLock) {
                if (activeCards.isEmpty()) {
                    break;
                }
                if (!paused) {
                    renderAllCards();
                    spinnerFrame = (spinnerFrame + 1) % SPINNER_FRAMES.length;
                }
            }
            try {
                Thread.sleep(SPINNER_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void renderAllCards() {
        if (ui == null || activeCards.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        String spinner = SPINNER_FRAMES[spinnerFrame];

        moveCursorUp(sb, activeCards.size());

        for (int i = 0; i < activeCards.size(); i++) {
            ToolCallCard card = activeCards.get(i);
            renderCardLine(sb, card, spinner);
            if (i < activeCards.size() - 1) {
                sb.append("\n");
            }
        }

        ui.print(sb.toString());
    }

    private void moveCursorUp(StringBuilder sb, int lines) {
        sb.append("\r");
        for (int i = 0; i < lines; i++) {
            sb.append("\033[1A");
        }
    }

    private void renderCardLine(StringBuilder sb, ToolCallCard card, String spinner) {
        int terminalWidth = getTerminalWidth();
        sb.append(" ".repeat(terminalWidth));
        sb.append("\r");

        String prefix = String.format("[%d/%d]", card.getIndex() + 1, card.getTotal());
        String content = String.format("  %s %s %s [%s] %s",
                card.gray(prefix),
                card.cyan(spinner),
                card.boldYellow(card.getToolName()),
                card.dim(card.getCurrentStatus()),
                card.gray(card.getElapsedTime()));

        sb.append(content);
    }

    public void pauseAll() {
        synchronized (renderLock) {
            if (paused) {
                return;
            }
            paused = true;
        }
    }

    public void resumeAll() {
        synchronized (renderLock) {
            if (!paused) {
                return;
            }
            paused = false;
        }
    }

    public void clear() {
        synchronized (renderLock) {
            activeCards.clear();
            paused = false;
            if (renderThread != null) {
                renderThread.interrupt();
                renderThread = null;
            }
        }
    }
}
