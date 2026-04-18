package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.lsp.model.Hover;
import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class HoverTool extends LspBaseTool {

    public HoverTool(LspClient lspClient, String languageId) {
        super(lspClient, languageId);
    }

    @Override
    public String getName() {
        return "lsp_" + languageId + "_hover";
    }

    @Override
    public String getDescription() {
        return "【LSP " + languageId + "】获取符号的详细文档和类型信息。" +
               "返回方法签名、类型定义、Javadoc 注释等。" +
               "用于：快速了解方法用途、参数含义、返回值类型，无需跳转到定义。" +
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

            Hover hover = lspClient.hover(file, line, column)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResult(hover);
        } catch (Exception e) {
            throw new ToolExecutionException("执行 hover 失败", e);
        }
    }

    private String formatResult(Hover hover) {
        List<String> contents = hover.getContentStrings();
        if (contents.isEmpty()) {
            return "❌ 未找到悬停信息。\n可能原因：LSP 还在索引中，或光标不在符号上。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 符号信息：\n\n");

        for (String content : contents) {
            sb.append(content).append("\n\n");
        }

        return sb.toString();
    }
}
