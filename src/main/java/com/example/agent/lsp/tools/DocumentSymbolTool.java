package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.lsp.model.SymbolInformation;
import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;

public class DocumentSymbolTool extends LspBaseTool {

    public DocumentSymbolTool(LspClient lspClient, String languageId) {
        super(lspClient, languageId);
    }

    @Override
    public String getName() {
        return "lsp_document_symbol";
    }

    @Override
    public String getDescription() {
        return "列出文件中的所有结构，包括类、方法、字段等";
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
                    }
                },
                "required": ["file"]
            }
            """;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!lspClient.isInitialized()) {
            return "ℹ️ LSP 服务正在启动中，暂时无法使用文档符号功能。\n" +
                   "建议：可以先用 read_file 读取文件结构，等待 LSP 初始化完成后再重试。\n" +
                   "提示：jdtls 首次启动需要 60-120 秒建立索引。";
        }
        
        try {
            String file = arguments.get("file").asText();

            List<SymbolInformation> symbols = lspClient.documentSymbol(file)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResults(symbols);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            if (msg.contains("未初始化") || msg.contains("连接未建立")) {
                return "ℹ️ LSP 服务正在启动中，暂时无法使用文档符号功能。\n" +
                       "建议：可以先用 read_file 读取文件结构，等待 LSP 初始化完成后再重试。";
            }
            return "⚠️ 获取文档符号失败: " + msg + "\n" +
                   "建议：检查文件路径是否正确。";
        }
    }

    private String formatResults(List<SymbolInformation> symbols) {
        if (symbols.isEmpty()) {
            return "❌ 未找到符号。\n可能原因：LSP 还在索引中，或文件为空。";
        }

        symbols.sort(Comparator.comparingInt(s -> s.getLocation().getRange().getStart().getLine()));

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 文件中的 ").append(symbols.size()).append(" 个符号：\n\n");

        for (SymbolInformation symbol : symbols) {
            String kind = String.format("%-12s", symbol.getKindName());
            int line = symbol.getLocation().getRange().getStart().getLine();
            String name = symbol.getName();
            if (symbol.getContainerName() != null && !symbol.getContainerName().isEmpty()) {
                name = symbol.getContainerName() + "." + name;
            }

            sb.append("[").append(kind).append("] (行").append(line).append(") ").append(name).append("\n");
        }

        return sb.toString();
    }
}
