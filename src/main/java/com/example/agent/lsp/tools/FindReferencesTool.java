package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.lsp.model.Location;
import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

public class FindReferencesTool extends LspBaseTool {

    public FindReferencesTool(LspClient lspClient, String languageId) {
        super(lspClient, languageId);
    }

    @Override
    public String getName() {
        return "lsp_" + languageId + "_references";
    }

    @Override
    public String getDescription() {
        return "【LSP " + languageId + "】精准查找所有引用该符号的位置。比 grep 准确 100 倍！" +
               "返回所有引用的文件路径、行号和列号。" +
               "用于：找到方法在哪里被调用、类在哪里被使用、变量在哪里被引用。" +
               "注意：行号和列号从 0 开始！";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "file": {
                        "type": "string",
                        "description": "源文件路径（相对于项目根目录）"
                    },
                    "line": {
                        "type": "integer",
                        "description": "行号（从 0 开始）"
                    },
                    "column": {
                        "type": "integer",
                        "description": "列号（从 0 开始）"
                    }
                },
                "required": ["file", "line", "column"]
            }
            """;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        try {
            String file = arguments.get("file").asText();
            int line = arguments.get("line").asInt();
            int column = arguments.get("column").asInt();

            List<Location> results = lspClient.references(file, line, column)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResults(results);
        } catch (Exception e) {
            throw new ToolExecutionException("执行 references 失败", e);
        }
    }

    private String formatResults(List<Location> locations) {
        if (locations.isEmpty()) {
            return "❌ 未找到任何引用。\n可能原因：LSP 还在索引中，或光标不在符号上。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 找到 ").append(locations.size()).append(" 个引用位置：\n\n");

        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            Path path = loc.toFilePath();
            sb.append(i + 1).append(". ").append(path).append("\n");
            sb.append("   行: ").append(loc.getRange().getStart().getLine());
            sb.append("  列: ").append(loc.getRange().getStart().getCharacter()).append("\n\n");
        }

        return sb.toString();
    }
}
