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
        return "lsp_find_references";
    }

    @Override
    public String getDescription() {
        return "查找所有引用位置，列出所有调用该方法/类的文件和位置";
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
        logger.info("【FindReferences】收到调用请求，完整参数: {}", arguments);
        
        if (!lspClient.isInitialized()) {
            logger.warn("【FindReferences】LSP 未初始化，请求参数: {}", arguments);
            return "ℹ️ LSP 服务正在启动中，暂时无法使用查找引用功能。\n" +
                   "建议：可以先用 grep 工具进行简单搜索，等待 LSP 初始化完成后再重试。\n" +
                   "提示：jdtls 首次启动需要 60-120 秒建立索引。";
        }
        
        String file = arguments.get("file").asText();
        int line = arguments.get("line").asInt();
        int column = arguments.get("column").asInt();

        try {
            logger.info("【FindReferences】调用 LSP references: file={}, line={}, column={}", file, line, column);

            List<Location> results = lspClient.references(file, line, column)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            logger.info("【FindReferences】LSP 响应成功，结果数量: {}", results.size());
            for (int i = 0; i < results.size(); i++) {
                Location loc = results.get(i);
                logger.info("【FindReferences】结果 {}: uri={}, range={}", 
                        i + 1, loc.getUri(), loc.getRange());
            }

            return formatResults(results);
        } catch (Exception e) {
            Throwable actual = unwrapException(e);
            String msg = actual.getMessage() != null ? actual.getMessage() : "未知错误";

            logger.error("【FindReferences】调用失败! 请求参数: file={}, line={}, column={}", 
                    file, line, column, actual);
            logger.error("【FindReferences】异常堆栈: ", actual);

            if (msg.contains("未初始化") || msg.contains("连接未建立")) {
                return "ℹ️ LSP 服务正在启动中，暂时无法使用查找引用功能。\n" +
                       "建议：可以先用 grep 工具进行简单搜索，等待 LSP 初始化完成后再重试。";
            }
            return "⚠️ 查找引用失败: " + msg + "\n" +
                   "建议：检查文件路径是否正确，行号/列号是否有效。";
        }
    }

    private String formatResults(List<Location> locations) {
        if (locations.isEmpty()) {
            if (lspClient.isIndexingInProgress()) {
                long elapsedSeconds = (System.currentTimeMillis() - lspClient.getInitializedTimestamp()) / 1000;
                return "ℹ️ LSP 正在建立索引中（已运行 " + elapsedSeconds + " 秒），暂未找到结果。\n" +
                       "提示：jdtls 完整索引通常需要 60-120 秒。\n" +
                       "建议：可以先用 grep 工具搜索，或稍后再重试查找引用。";
            }
            return "❌ 未找到任何引用。\n可能原因：光标不在符号名称上，请调整行号列号后重试。";
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
