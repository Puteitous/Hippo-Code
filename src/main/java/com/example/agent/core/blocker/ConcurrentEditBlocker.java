package com.example.agent.core.blocker;

import com.example.agent.tools.concurrent.FileLockManager;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class ConcurrentEditBlocker implements Blocker {

    private final FileLockManager lockManager = FileLockManager.getInstance();
    private final List<String> writeTools = List.of("edit_file", "write_file", "delete_file");

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if (!writeTools.contains(toolName)) {
            return HookResult.allow();
        }

        if (!arguments.has("path")) {
            return HookResult.allow();
        }

        String path = arguments.get("path").asText();

        if (lockManager.isLocked(path)) {
            return HookResult.deny(
                String.format("文件 %s 正在被其他操作编辑中", path),
                "请等待当前编辑完成后再尝试，或确认是否有并发编辑冲突"
            );
        }

        return HookResult.allow();
    }
}
