package com.example.agent.tools;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToolArgumentSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(ToolArgumentSanitizer.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UNESCAPED_UNICODE_PATTERN = Pattern.compile("[^\\\\]u([0-9a-fA-F]{4})");

    private ToolArgumentSanitizer() {
    }

    public static List<Message> sanitizeContext(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        int fixedCount = 0;
        for (Message message : messages) {
            if (message.getToolCalls() != null) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (toolCall.getFunction() != null) {
                    String toolName = toolCall.getFunction().getName();
                    String original = toolCall.getFunction().getArguments();
                    String fixed = fixJsonArguments(toolName, original);
                    if (original != fixed && !original.equals(fixed)) {
                        toolCall.getFunction().setArguments(fixed);
                        fixedCount++;
                    }
                }
            }
        }
    }

        if (fixedCount > 0) {
            logger.debug("已清理会话上下文，修复了 {} 个损坏的 toolCall 参数", fixedCount);
        }

        return messages;
    }

    public static String fixJsonArguments(String toolName, String arguments) {
        if (!requiresFixing(toolName) || arguments == null || arguments.isEmpty()) {
            return arguments;
        }

        if (isValidJson(arguments)) {
            return arguments;
        }

        logger.debug("检测到 {} 的 JSON 参数无效，尝试修复...", toolName);
        
        String fixed = arguments;
        
        // 1. 修复字段值的转义问题
        fixed = fixFieldValue(fixed, "old_text");
        fixed = fixFieldValue(fixed, "new_text");
        fixed = fixFieldValue(fixed, "content");
        fixed = fixFieldValue(fixed, "path");
        fixed = fixFieldValue(fixed, "query");
        
        // 2. 尝试修复常见的 JSON 格式问题
        fixed = fixCommonJsonIssues(fixed);

        if (!fixed.equals(arguments)) {
            logger.debug("已修复 {} 参数的 JSON 问题，尝试验证...", toolName);
            if (isValidJson(fixed)) {
                logger.debug("✅ {} 参数修复成功", toolName);
                return fixed;
            } else {
                logger.warn("⚠️ {} 参数修复后仍然无效，尝试更激进的修复策略", toolName);
            }
        }
        
        // 3. 如果仍然无效，尝试使用更宽松的解析器
        try {
            JsonNode node = OBJECT_MAPPER.readTree(fixed);
            logger.debug("✅ 使用宽松模式解析成功");
            return fixed;
        } catch (Exception e) {
            logger.warn("宽松模式解析失败：{}", e.getMessage());
        }

        // 4. 最后的尝试：提取并重新构建 JSON
        String reconstructed = tryReconstructJson(fixed);
        if (reconstructed != null && isValidJson(reconstructed)) {
            logger.debug("✅ 成功重建 JSON");
            return reconstructed;
        }

        logger.error("❌ 所有修复策略均失败，返回原始参数（可能导致后续错误）");
        return arguments;
    }

    private static String fixCommonJsonIssues(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        String fixed = json;
        
        // 修复未转义的双引号
        fixed = fixUnescapedQuotes(fixed);
        
        // 修复缺失的逗号
        fixed = fixMissingCommas(fixed);
        
        // 修复多余的逗号
        fixed = fixTrailingCommas(fixed);
        
        // 修复单引号为双引号
        fixed = fixSingleQuotes(fixed);
        
        // 修复未转义的控制字符
        fixed = fixControlCharacters(fixed);

        return fixed;
    }

    private static String fixUnescapedQuotes(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        StringBuilder result = new StringBuilder(json.length() + 100);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                result.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                result.append(c);
                continue;
            }

            if (c == ':' && !inString) {
                result.append(c);
                continue;
            }

            if (c == '"' && !inString) {
                result.append("\\\"");
                continue;
            }

            result.append(c);
        }

        return result.toString();
    }

    private static String fixMissingCommas(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        // 在 }{ 或 ][ 或 }[ 或 ]{ 之间添加逗号
        String fixed = json.replaceAll("\\}\\s*\\{", "},{");
        fixed = fixed.replaceAll("\\]\\s*\\[", "],[");
        fixed = fixed.replaceAll("\\}\\s*\\[", "],[");
        fixed = fixed.replaceAll("\\]\\s*\\{", "],[");
        
        // 在值后面缺少逗号的地方添加
        fixed = fixed.replaceAll("\"\\s*\\n\\s*\"", "\",\n\"");
        fixed = fixed.replaceAll("\\d\\s*\\n\\s*\"", ",\n\"");
        fixed = fixed.replaceAll("true\\s*\\n\\s*\"", ",\n\"");
        fixed = fixed.replaceAll("false\\s*\\n\\s*\"", ",\n\"");
        fixed = fixed.replaceAll("null\\s*\\n\\s*\"", ",\n\"");
        
        return fixed;
    }

    private static String fixTrailingCommas(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        // 移除最后一个逗号在 } 或 ] 之前
        return json.replaceAll(",\\s*([\\}\\]])", "$1");
    }

    private static String fixSingleQuotes(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        // 将键和值的单引号都替换为双引号
        String fixed = json;
        
        // 替换键的单引号：'key': -> "key":
        fixed = fixed.replaceAll("'([^']*?)'\\s*:", "\"$1\":");
        
        // 替换值的单引号（在冒号后面）：: 'value' -> : "value"
        // 匹配 : 后面的单引号字符串，直到下一个逗号、} 或 ]
        fixed = fixed.replaceAll(":\\s*'([^']*?)'(\\s*[,}\\]])", ":\"$1\"$2");
        
        return fixed;
    }

    private static String fixControlCharacters(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        StringBuilder result = new StringBuilder(json.length());
        for (char c : json.toCharArray()) {
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                // 替换未转义的控制字符
                result.append("\\u").append(String.format("%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String tryReconstructJson(String brokenJson) {
        try {
            // 尝试提取关键的键值对并重新构建
            StringBuilder reconstructed = new StringBuilder("{");
            boolean first = true;
            
            // 简单的键值对提取（适用于扁平结构）
            Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(brokenJson);
            
            while (matcher.find()) {
                if (!first) {
                    reconstructed.append(",");
                }
                String key = matcher.group(1);
                String value = matcher.group(2);
                reconstructed.append("\"").append(key).append("\":\"").append(value).append("\"");
                first = false;
            }
            
            reconstructed.append("}");
            
            if (reconstructed.length() > 2) {
                return reconstructed.toString();
            }
        } catch (Exception e) {
            logger.debug("重建 JSON 失败：{}", e.getMessage());
        }
        
        return null;
    }

    public static boolean requiresFixing(String toolName) {
        // 所有工具都可能需要修复 JSON 问题
        return true;
    }

    private static boolean isValidJson(String json) {
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            while (parser.nextToken() != null) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String fixFieldValue(String json, String fieldName) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        String pattern1 = "\"" + fieldName + "\":\"";
        String pattern2 = "\"" + fieldName + "\": \"";

        int startIdx = findFieldStartIndex(json, pattern1, pattern2);
        if (startIdx == -1) {
            return json;
        }

        StringWriter result = new StringWriter(json.length() + 200);
        result.write(json, 0, startIdx);

        int pos = startIdx;
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inEscape = false;

        while (pos < json.length()) {
            char c = json.charAt(pos);

            if (inEscape) {
                if (!isValidEscapeSequence(c)) {
                    result.write('\\');
                }
                result.write(c);
                inEscape = false;
                pos++;
                continue;
            }

            if (c == '\\') {
                inEscape = true;
                pos++;
                continue;
            }

            if (c == '"') {
                if (isStringEnd(json, pos, braceDepth, bracketDepth)) {
                    result.write(json.substring(pos));
                    break;
                } else {
                    result.write("\\\"");
                    pos++;
                    continue;
                }
            }

            if (c == '{') braceDepth++;
            if (c == '}') braceDepth--;
            if (c == '[') bracketDepth++;
            if (c == ']') bracketDepth--;

            result.write(escapeSpecialChar(c));
            pos++;
        }

        return result.toString();
    }

    private static int findFieldStartIndex(String json, String pattern1, String pattern2) {
        int idx1 = json.indexOf(pattern1);
        int idx2 = json.indexOf(pattern2);

        if (idx1 >= 0 && idx2 >= 0) {
            return Math.min(idx1, idx2) + (idx1 < idx2 ? pattern1.length() : pattern2.length());
        } else if (idx1 >= 0) {
            return idx1 + pattern1.length();
        } else if (idx2 >= 0) {
            return idx2 + pattern2.length();
        }
        return -1;
    }

    private static boolean isValidEscapeSequence(char c) {
        return c == '"' || c == '\\' || c == '/' || c == 'b' 
            || c == 'f' || c == 'n' || c == 'r' || c == 't'
            || c == 'u';
    }

    private static boolean isStringEnd(String json, int quotePos, int braceDepth, int bracketDepth) {
        if (braceDepth > 0 || bracketDepth > 0) {
            return false;
        }

        int next = quotePos + 1;
        while (next < json.length() && Character.isWhitespace(json.charAt(next))) {
            next++;
        }

        if (next >= json.length()) {
            return true;
        }

        char nextChar = json.charAt(next);
        
        if (nextChar == ',' || nextChar == '}' || nextChar == ']') {
            int trailingBraces = 0;
            int trailingBrackets = 0;
            for (int i = next; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') trailingBraces++;
                if (c == '}') trailingBraces--;
                if (c == '[') trailingBrackets++;
                if (c == ']') trailingBrackets--;
            }
            return trailingBraces <= 0 && trailingBrackets <= 0;
        }

        return false;
    }

    private static String escapeSpecialChar(char c) {
        switch (c) {
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            case '\b':
                return "\\b";
            case '\f':
                return "\\f";
            default:
                return String.valueOf(c);
        }
    }
}
