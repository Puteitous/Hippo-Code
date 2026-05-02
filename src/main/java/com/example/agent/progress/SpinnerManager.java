package com.example.agent.progress;

import com.example.agent.console.AgentUi;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.core.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

public class SpinnerManager {

    private static final SpinnerManager INSTANCE = new SpinnerManager();
    private final List<ToolCallCard> activeCards = new ArrayList<>();
    private final AgentUi ui;

    private SpinnerManager() {
        AgentUi agentUi = null;
        try {
            agentUi = ServiceLocator.get(AgentUi.class);
        } catch (Exception e) {
            // 忽略异常，在测试环境中 ServiceLocator 可能未就绪
        }
        this.ui = agentUi;
    }

    public static SpinnerManager getInstance() {
        return INSTANCE;
    }

    public void registerCard(ToolCallCard card) {
        synchronized (activeCards) {
            activeCards.add(card);
        }
        printCardStart(card);
    }

    public void unregisterCard(ToolCallCard card) {
        synchronized (activeCards) {
            activeCards.remove(card);
        }
    }

    private void printCardStart(ToolCallCard card) {
        if (ui == null) {
            return;
        }
        String prefix = String.format("[%d/%d]", card.getIndex() + 1, card.getTotal());
        String msg = String.format("  %s ⠙ %s [%s]",
                ConsoleStyle.gray(prefix),
                ConsoleStyle.boldYellow(card.getToolName()),
                ConsoleStyle.dim("执行中..."));
        ui.println(msg);
    }

    public void pauseAll() {
    }

    public void resumeAll() {
    }

    public void clear() {
        synchronized (activeCards) {
            activeCards.clear();
        }
    }
}
