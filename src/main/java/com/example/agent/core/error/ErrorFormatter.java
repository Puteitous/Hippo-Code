package com.example.agent.core.error;

public class ErrorFormatter {

    public static String format(AgentException e) {
        return String.format(
            "%s\n💡 建议：%s",
            e.getMessage(),
            e.getSuggestion()
        );
    }

    public static String formatBlocked(String reason) {
        return String.format(
            "[BLOCKED] 操作被阻断\n原因：%s",
            reason
        );
    }

    public static String formatForAi(AgentException e) {
        String detail = e.getDetail() != null ? ": " + e.getDetail() : "";
        return String.format("%s%s", e.getErrorCode().name(), detail);
    }
}
