package com.example.agent.tools;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.List;

public class ToolArgumentSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(ToolArgumentSanitizer.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

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

        String fixed = arguments;
        fixed = fixFieldValue(fixed, "old_text");
        fixed = fixFieldValue(fixed, "new_text");
        fixed = fixFieldValue(fixed, "content");

        if (!fixed.equals(arguments) && isValidJson(fixed)) {
            logger.debug("已修复 {} 参数的 JSON 转义问题", toolName);
        }

        return fixed;
    }

    public static boolean requiresFixing(String toolName) {
        return "edit_file".equals(toolName) || "write_file".equals(toolName);
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
