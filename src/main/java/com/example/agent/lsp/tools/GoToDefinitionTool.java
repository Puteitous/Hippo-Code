package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.lsp.model.LocationLink;
import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

public class GoToDefinitionTool extends LspBaseTool {

    public GoToDefinitionTool(LspClient lspClient, String languageId) {
        super(lspClient, languageId);
    }

    @Override
    public String getName() {
        return "lsp_" + languageId + "_definition";
    }

    @Override
    public String getDescription() {
        return "【LSP " + languageId + "】精准查找符号的定义位置。比 grep 准确 100 倍！" +
               "返回定义所在的文件路径、行号和列号。" +
               "用于：找到方法定义、类声明、变量创建位置。" +
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

            List<LocationLink> results = lspClient.definition(file, line, column)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResults(results);
        } catch (Exception e) {
            throw new ToolExecutionException("执行 definition 失败", e);
        }
    }

    private String formatResults(List<LocationLink> links) {
        if (links.isEmpty()) {
            return "❌ 未找到定义位置。\n可能原因：LSP 还在索引中，或光标不在符号上。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 找到 ").append(links.size()).append(" 个定义位置：\n\n");

        for (int i = 0; i < links.size(); i++) {
            LocationLink link = links.get(i);
            Path path = link.toFilePath();
            sb.append(i + 1).append(". ").append(path).append("\n");
            sb.append("   行: ").append(link.getTargetRange().getStart().getLine());
            sb.append("  列: ").append(link.getTargetRange().getStart().getCharacter()).append("\n\n");
        }

        return sb.toString();
    }
}
