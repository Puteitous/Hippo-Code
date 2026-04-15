package com.example.agent.memory.summarizer;

import com.example.agent.llm.model.Message;
import com.example.agent.memory.model.MemoryTag;
import com.example.agent.memory.model.PrioritizedMessage;

import java.util.ArrayList;
import java.util.List;

public class ExtractiveSummarizer implements SummaryGenerator {

    private static final int MAX_REQUIREMENTS = 5;
    private static final int MAX_ERRORS = 3;

    @Override
    public Message generate(List<PrioritizedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        StringBuilder summary = new StringBuilder(2048);
        summary.append("\n");
        summary.append("=== 📝 历史对话摘要 ===\n");
        summary.append("\n");

        List<String> requirements = extractUserRequirements(messages);
        if (!requirements.isEmpty()) {
            summary.append("📌 用户核心需求（最近 ").append(Math.min(requirements.size(), MAX_REQUIREMENTS)).append(" 条）：\n");
            requirements.stream()
                    .limit(MAX_REQUIREMENTS)
                    .forEach(req -> summary.append("  • ").append(truncate(req, 80)).append("\n"));
            summary.append("\n");
        }

        List<String> errors = extractErrorInfo(messages);
        if (!errors.isEmpty()) {
            summary.append("⚠️ 关键错误信息（最近 ").append(Math.min(errors.size(), MAX_ERRORS)).append(" 条）：\n");
            errors.stream()
                    .limit(MAX_ERRORS)
                    .forEach(err -> summary.append("  • ").append(truncate(err, 100)).append("\n"));
            summary.append("\n");
        }

        summary.append("📊 进度统计：\n");
        summary.append("  • 已进行 ").append(countTurns(messages)).append(" 轮对话\n");
        summary.append("  • 已执行 ").append(countToolCalls(messages)).append(" 个工具调用\n");
        summary.append("  • 摘要包含 ").append(messages.size()).append(" 条历史消息\n");
        summary.append("\n");

        summary.append("=== 摘要结束 ===\n");
        summary.append("\n");

        return Message.system(summary.toString());
    }

    private List<String> extractUserRequirements(List<PrioritizedMessage> messages) {
        List<String> requirements = new ArrayList<>();
        for (PrioritizedMessage pm : reversed(messages)) {
            if (pm.hasTag(MemoryTag.USER_REQUIREMENT) && "user".equals(pm.getMessage().getRole())) {
                String content = pm.getMessage().getContent();
                if (content != null && !content.isBlank()) {
                    requirements.add(content.trim());
                }
            }
        }
        return requirements;
    }

    private List<String> extractErrorInfo(List<PrioritizedMessage> messages) {
        List<String> errors = new ArrayList<>();
        for (PrioritizedMessage pm : reversed(messages)) {
            if (pm.hasTag(MemoryTag.ERROR_INFO)) {
                String content = pm.getMessage().getContent();
                if (content != null && !content.isBlank()) {
                    errors.add(content.trim());
                }
            }
        }
        return errors;
    }

    private int countTurns(List<PrioritizedMessage> messages) {
        return (int) messages.stream()
                .filter(pm -> "user".equals(pm.getMessage().getRole()))
                .count();
    }

    private int countToolCalls(List<PrioritizedMessage> messages) {
        return (int) messages.stream()
                .filter(pm -> pm.hasTag(MemoryTag.TOOL_RESULT))
                .count();
    }

    private List<PrioritizedMessage> reversed(List<PrioritizedMessage> messages) {
        List<PrioritizedMessage> reversed = new ArrayList<>(messages);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
