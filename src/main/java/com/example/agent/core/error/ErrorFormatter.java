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
        return String.format(
            "错误码: %s\n错误信息: %s%s\n建议操作: %s",
            e.getErrorCode().getCode(),
            e.getErrorCode().getMessage(),
            e.getDetail() != null ? "\n详细信息: " + e.getDetail() : "",
            e.getSuggestion()
        );
    }
}
