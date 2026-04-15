package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PathExistenceBlocker implements Blocker {

    private final List<String> pathRequiredTools = List.of(
        "edit_file", "read_file"
    );

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if (!pathRequiredTools.contains(toolName)) {
            return HookResult.allow();
        }

        if (!arguments.has("path")) {
            return HookResult.allow();
        }

        String pathStr = arguments.get("path").asText();
        Path path = Paths.get(pathStr);

        if (!Files.exists(path)) {
            return HookResult.deny(
                String.format("路径不存在: %s", pathStr),
                String.format("请先使用 glob 或 grep 搜索确认文件位置，或确认路径是否正确")
            );
        }

        if (Files.isDirectory(path)) {
            return HookResult.deny(
                String.format("路径是目录而不是文件: %s", pathStr),
                "请指定具体的文件路径，或使用 ls 查看目录内容"
            );
        }

        return HookResult.allow();
    }
}
