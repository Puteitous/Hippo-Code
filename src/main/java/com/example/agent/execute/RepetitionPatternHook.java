package com.example.agent.execute;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RepetitionPatternHook implements StopHook {

    private static final Logger logger = LoggerFactory.getLogger(RepetitionPatternHook.class);
    private static final int MAX_CONSECUTIVE_REPETITION = 3;

    @Override
    public StopHook.StopHookResult evaluate(StopHook.StopHookContext context) {
        List<Message> messages = context.getRecentMessages();
        List<String> recentToolCalls = extractToolCallSignatures(messages);

        if (hasConsecutiveRepetition(recentToolCalls, MAX_CONSECUTIVE_REPETITION)) {
            String repeatedTool = recentToolCalls.get(recentToolCalls.size() - 1);
            String reason = String.format(
                "检测到重复模式：连续 %d 轮执行相同的工具调用 [%s]，强制终止循环",
                MAX_CONSECUTIVE_REPETITION,
                repeatedTool
            );
            
            // 详细日志：输出最近 3 轮的消息摘要和工具参数
            logger.warn("═══════════════════════════════════════════════════════════");
            logger.warn("RepetitionPatternHook 触发终止");
            logger.warn("═══════════════════════════════════════════════════════════");
            logger.warn("重复工具：{}", repeatedTool);
            logger.warn("检测轮次：{}", context.getTurnCount());
            
            // 输出最近 3 轮助手消息的详细信息
            List<Message> assistantMessages = new ArrayList<>();
            for (int i = messages.size() - 1; i >= 0 && assistantMessages.size() < 3; i--) {
                if (messages.get(i).isAssistant() && messages.get(i).getToolCalls() != null) {
                    assistantMessages.add(0, messages.get(i));
                }
            }
            
            logger.warn("───────────────────────────────────────────────────────────");
            logger.warn("最近 {} 轮助手消息详情:", assistantMessages.size());
            logger.warn("───────────────────────────────────────────────────────────");
            
            for (int i = 0; i < assistantMessages.size(); i++) {
                Message msg = assistantMessages.get(i);
                logger.warn("【第 {} 轮】", context.getTurnCount() - assistantMessages.size() + i + 1);
                logger.warn("  工具调用数量：{}", msg.getToolCalls().size());
                
                for (int j = 0; j < msg.getToolCalls().size(); j++) {
                    ToolCall tc = msg.getToolCalls().get(j);
                    String toolName = tc.getFunction() != null ? tc.getFunction().getName() : "unknown";
                    String args = tc.getFunction() != null && tc.getFunction().getArguments() != null 
                        ? tc.getFunction().getArguments() : "null";
                    
                    // 对于 glob 工具，额外提取 pattern 参数进行显示
                    if ("glob".equals(toolName) && args != null && !args.equals("null")) {
                        String pattern = extractGlobPattern(args);
                        logger.warn("    [工具 {}] {} - pattern: {}", j + 1, toolName, pattern);
                        logger.warn("                完整参数：{}", truncate(args, 200));
                    } else {
                        logger.warn("    [工具 {}] {} - 参数：{}", j + 1, toolName, truncate(args, 200));
                    }
                }
                
                // 输出助手回答的摘要（前 100 字符）
                String content = msg.getContent() != null ? msg.getContent() : "";
                if (!content.isEmpty()) {
                    logger.warn("  助手回答摘要：{}...", truncate(content, 100));
                }
                logger.warn("");
            }
            
            logger.warn("═══════════════════════════════════════════════════════════");
            
            return StopHook.StopHookResult.stop(reason);
        }

        return StopHook.StopHookResult.continueExecution();
    }

    private List<String> extractToolCallSignatures(List<Message> messages) {
        List<String> signatures = new ArrayList<>();

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.isAssistant() && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                String signature = buildToolCallSignature(msg.getToolCalls());
                signatures.add(0, signature);
            }
        }

        return signatures;
    }

    private String buildToolCallSignature(List<ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return "no_tool_calls";
        }

        List<String> signatures = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            if (tc.getFunction() != null && tc.getFunction().getName() != null) {
                String toolName = tc.getFunction().getName();
                String args = tc.getFunction().getArguments();
                
                // 对于有参数的工具，包含关键参数特征
                if (args != null && !args.isEmpty() && !args.equals("null")) {
                    String paramSignature = extractKeyParameters(toolName, args);
                    if (paramSignature != null && !paramSignature.isEmpty()) {
                        signatures.add(toolName + "(" + paramSignature + ")");
                    } else {
                        signatures.add(toolName);
                    }
                } else {
                    signatures.add(toolName);
                }
            }
        }

        return String.join(",", signatures);
    }
    
    /**
     * 提取工具的关键参数特征，用于区分不同的调用
     */
    private String extractKeyParameters(String toolName, String jsonArgs) {
        try {
            switch (toolName) {
                case "list_directory":
                case "read_file":
                case "edit_file":
                case "write_file":
                    // 提取 path 参数
                    return extractJsonString(jsonArgs, "path");
                    
                case "glob":
                    // 提取 pattern 参数
                    return extractJsonString(jsonArgs, "pattern");
                    
                case "grep":
                    // 提取 pattern 参数
                    return extractJsonString(jsonArgs, "pattern");
                    
                case "bash":
                    // 提取 command 参数（截断）
                    String cmd = extractJsonString(jsonArgs, "command");
                    if (cmd != null && cmd.length() > 50) {
                        return cmd.substring(0, 50) + "...";
                    }
                    return cmd;
                    
                default:
                    // 其他工具不提取参数
                    return null;
            }
        } catch (Exception e) {
            logger.debug("提取参数失败：tool={}, error={}", toolName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JSON 中提取指定字符串字段
     */
    private String extractJsonString(String json, String fieldName) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        
        if (m.find()) {
            return m.group(1);
        }
        
        return null;
    }

    private boolean hasConsecutiveRepetition(List<String> signatures, int threshold) {
        if (signatures.size() < threshold) {
            return false;
        }

        for (int i = signatures.size() - threshold; i >= 0; i--) {
            boolean allSame = true;
            String first = signatures.get(i);

            for (int j = 1; j < threshold; j++) {
                if (!first.equals(signatures.get(i + j))) {
                    allSame = false;
                    break;
                }
            }

            if (allSame) {
                return true;
            }
        }

        return false;
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 从 glob 工具的 JSON 参数中提取 pattern 字段
     */
    private String extractGlobPattern(String jsonArgs) {
        try {
            // 简单的 JSON 解析，提取 pattern 字段
            int patternIndex = jsonArgs.indexOf("\"pattern\"");
            if (patternIndex == -1) {
                return "(未找到 pattern 字段)";
            }
            
            // 找到冒号
            int colonIndex = jsonArgs.indexOf(':', patternIndex);
            if (colonIndex == -1) {
                return "(解析失败)";
            }
            
            // 跳过空白字符
            int startQuote = colonIndex + 1;
            while (startQuote < jsonArgs.length() && 
                   Character.isWhitespace(jsonArgs.charAt(startQuote))) {
                startQuote++;
            }
            
            // 找到起始引号
            if (startQuote >= jsonArgs.length() || jsonArgs.charAt(startQuote) != '"') {
                return "(解析失败)";
            }
            
            // 找到结束引号
            int endQuote = jsonArgs.indexOf('"', startQuote + 1);
            if (endQuote == -1) {
                return "(解析失败)";
            }
            
            return jsonArgs.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return "(解析异常：" + e.getMessage() + ")";
        }
    }
}
