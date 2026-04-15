package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EditBeforeReadBlocker implements Blocker {

    private final Set<String> readFiles = ConcurrentHashMap.newKeySet();
    private final List<String> editTools = List.of("edit_file", "write_file");

    private FileOperationStateMachine stateMachine;

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if ("read_file".equals(toolName) && arguments.has("path")) {
            readFiles.add(arguments.get("path").asText());
            return HookResult.allow();
        }

        if (editTools.contains(toolName)) {
            return checkEditOperation(toolName, arguments);
        }

        return HookResult.allow();
    }

    private HookResult checkEditOperation(String toolName, JsonNode arguments) {
        if (!arguments.has("path")) {
            return HookResult.allow();
        }

        String pathStr = arguments.get("path").asText();

        if (stateMachine != null && stateMachine.isNewlyCreated(pathStr)) {
            return HookResult.allow();
        }

        if (!readFiles.contains(pathStr)) {
            return HookResult.deny(
                String.format("编辑 %s 前未读取文件内容", pathStr),
                String.format("请先调用 read_file 获取 %s 的最新内容后再编辑", pathStr)
            );
        }

        return HookResult.allow();
    }

    public void setStateMachine(FileOperationStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public void reset() {
        readFiles.clear();
    }

    public boolean hasReadFile(String path) {
        return readFiles.contains(path);
    }
}
