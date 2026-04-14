package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;

public interface Blocker {

    String check(String toolName, JsonNode arguments);
}
