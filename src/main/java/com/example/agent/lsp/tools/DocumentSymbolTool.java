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
        return "lsp_" + languageId + "_document_symbol";
    }

    @Override
    public String getDescription() {
        return "【LSP " + languageId + "】列出文件中的所有符号（类、方法、字段等）。" +
               "返回符号名称、类型（CLASS/METHOD/FIELD等）和位置。" +
               "用于：快速了解文件结构，概览类中有哪些方法和字段。";
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
        try {
            String file = arguments.get("file").asText();

            List<SymbolInformation> symbols = lspClient.documentSymbol(file)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResults(symbols);
        } catch (Exception e) {
            throw new ToolExecutionException("执行 documentSymbol 失败", e);
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
