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
    public HookResult check(String toolName, JsonNode arguments) {
        for (Blocker blocker : blockers) {
            HookResult result = blocker.check(toolName, arguments);
            if (!result.isAllowed()) {
                return result;
            }
        }
        return HookResult.allow();
    }

    public List<Blocker> getBlockers() {
        return new ArrayList<>(blockers);
    }
}
