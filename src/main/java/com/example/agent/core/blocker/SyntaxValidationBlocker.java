package com.example.agent.core.blocker;

import com.example.agent.domain.ast.CodeParser;
import com.example.agent.domain.ast.ParseResult;
import com.example.agent.domain.ast.TreeSitterJavaParser;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyntaxValidationBlocker implements Blocker {

    private static final Logger logger = LoggerFactory.getLogger(SyntaxValidationBlocker.class);

    private final Map<String, CodeParser> parsers = new HashMap<>();
    private final List<String> targetTools = List.of("edit_file", "write_file");

    public SyntaxValidationBlocker() {
        parsers.put("java", new TreeSitterJavaParser());
    }

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        if (!targetTools.contains(toolName)) {
            return HookResult.allow();
        }

        try {
            String path = arguments.has("path") ? arguments.get("path").asText() : "";
            String code = extractCode(toolName, arguments);

            if (code == null || code.trim().isEmpty()) {
                return HookResult.allow();
            }

            CodeParser parser = selectParser(path);
            if (parser == null) {
                return HookResult.allow();
            }

            ParseResult result = parser.parse(code);
            if (!result.isValid()) {
                String suggestion = getSuggestionForErrors(result);

                return HookResult.deny(
                    String.format("检测到 %d 个语法错误\n%s",
                        result.getErrorCount(),
                        result.formatErrors()),
                    suggestion
                );
            }

        } catch (Exception e) {
            logger.debug("语法检查跳过: {}", e.getMessage());
        }

        return HookResult.allow();
    }

    private String extractCode(String toolName, JsonNode arguments) {
        if ("edit_file".equals(toolName) && arguments.has("new_text")) {
            return arguments.get("new_text").asText();
        }
        if ("write_file".equals(toolName) && arguments.has("content")) {
            return arguments.get("content").asText();
        }
        return null;
    }

    private CodeParser selectParser(String path) {
        for (CodeParser parser : parsers.values()) {
            if (parser.supports(path)) {
                return parser;
            }
        }
        return null;
    }

    private String getSuggestionForErrors(ParseResult result) {
        StringBuilder suggestion = new StringBuilder();
        suggestion.append("请修正上述语法错误后再重试。\n");
        suggestion.append("常见问题检查：\n");
        suggestion.append("  • 每行结尾是否有分号 ;\n");
        suggestion.append("  • 花括号 {} 是否匹配\n");
        suggestion.append("  • 圆括号 () 是否匹配\n");
        suggestion.append("  • 方括号 [] 是否匹配\n");
        suggestion.append("  • 所有的引号 \"\" '' 是否闭合");

        return suggestion.toString();
    }
}
