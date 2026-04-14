package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class BlockerChain implements Blocker {

    private final List<Blocker> blockers = new ArrayList<>();

    public BlockerChain add(Blocker blocker) {
        blockers.add(blocker);
        return this;
    }

    @Override
    public String check(String toolName, JsonNode arguments) {
        for (Blocker blocker : blockers) {
            String reason = blocker.check(toolName, arguments);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    public List<Blocker> getBlockers() {
        return new ArrayList<>(blockers);
    }
}
