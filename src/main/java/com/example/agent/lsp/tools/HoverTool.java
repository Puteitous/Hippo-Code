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
        return "lsp_hover";
    }

    @Override
    public String getDescription() {
        return "获取符号的文档和类型信息，包括方法签名、参数、返回值说明";
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
        if (!lspClient.isInitialized()) {
            return "ℹ️ LSP 服务正在启动中，暂时无法使用悬停信息功能。\n" +
                   "建议：可以先用 read_file 读取文件内容，等待 LSP 初始化完成后再重试。\n" +
                   "提示：jdtls 首次启动需要 60-120 秒建立索引。";
        }
        
        try {
            String file = arguments.get("file").asText();
            int line = arguments.get("line").asInt();
            int column = arguments.get("column").asInt();

            Hover hover = lspClient.hover(file, line, column)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResult(hover);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            if (msg.contains("未初始化") || msg.contains("连接未建立")) {
                return "ℹ️ LSP 服务正在启动中，暂时无法使用悬停信息功能。\n" +
                       "建议：可以先用 read_file 读取文件内容，等待 LSP 初始化完成后再重试。";
            }
            return "⚠️ 获取悬停信息失败: " + msg + "\n" +
                   "建议：检查文件路径是否正确，行号/列号是否有效。";
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
