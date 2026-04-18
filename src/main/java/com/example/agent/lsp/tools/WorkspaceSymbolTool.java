package com.example.agent.lsp.tools;

import com.example.agent.lsp.LspClient;
import com.example.agent.lsp.model.SymbolInformation;
import com.example.agent.tools.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class WorkspaceSymbolTool extends LspBaseTool {

    public WorkspaceSymbolTool(LspClient lspClient, String languageId) {
        super(lspClient, languageId);
    }

    @Override
    public String getName() {
        return "lsp_" + languageId + "_workspace_symbol";
    }

    @Override
    public String getDescription() {
        return "【LSP " + languageId + "】在整个项目中搜索符号。" +
               "支持模糊匹配，快速找到类、方法、字段的定义位置。" +
               "用于：跨文件查找，不需要知道符号在哪个文件中。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "符号名称搜索词（支持模糊匹配）"
                    }
                },
                "required": ["query"]
            }
            """;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        try {
            String query = arguments.get("query").asText();

            List<SymbolInformation> symbols = lspClient.workspaceSymbol(query)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            return formatResults(symbols);
        } catch (Exception e) {
            throw new ToolExecutionException("执行 workspace/symbol 失败", e);
        }
    }

    private String formatResults(List<SymbolInformation> symbols) {
        if (symbols.isEmpty()) {
            return "❌ 未找到匹配的符号。\n可能原因：LSP 还在索引中，或搜索词不匹配。";
        }

        symbols.sort(Comparator.comparing(SymbolInformation::getName));

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 找到 ").append(symbols.size()).append(" 个匹配的符号：\n\n");

        for (int i = 0; i < Math.min(symbols.size(), 50); i++) {
            SymbolInformation symbol = symbols.get(i);
            Path path = symbol.toFilePath();
            String kind = String.format("%-12s", symbol.getKindName());

            sb.append(i + 1).append(". [").append(kind).append("] ")
                    .append(symbol.getName()).append("\n");
            sb.append("   ").append(path).append(" (行")
                    .append(symbol.getLocation().getRange().getStart().getLine()).append(")\n\n");
        }

        if (symbols.size() > 50) {
            sb.append("... 还有 ").append(symbols.size() - 50).append(" 个结果已省略\n");
        }

        return sb.toString();
    }
}
