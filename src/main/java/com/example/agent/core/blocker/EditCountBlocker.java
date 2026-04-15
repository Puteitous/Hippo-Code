package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditCountBlocker implements Blocker {

    private static final int MAX_EDITS_PER_FILE = 5;
    private final Map<String, Integer> editCounts = new HashMap<>();
    private final List<String> editTools = List.of("edit_file", "write_file");

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if (!editTools.contains(toolName)) {
            return HookResult.allow();
        }

        List<String> paths = getAffectedPaths(toolName, arguments);
        for (String path : paths) {
            int count = editCounts.getOrDefault(path, 0) + 1;
            if (count > MAX_EDITS_PER_FILE) {
                return HookResult.deny(
                    String.format("文件 %s 已被编辑 %d 次", path, MAX_EDITS_PER_FILE),
                    "停止打补丁，先理解根本原因，重新分析问题后再尝试修改"
                );
            }
            editCounts.put(path, count);
        }

        return HookResult.allow();
    }

    private List<String> getAffectedPaths(String toolName, JsonNode arguments) {
        if (arguments.has("path")) {
            return List.of(arguments.get("path").asText());
        }
        return List.of();
    }

    public void reset() {
        editCounts.clear();
    }

    public int getEditCount(String path) {
        return editCounts.getOrDefault(path, 0);
    }
}
