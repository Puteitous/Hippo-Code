package com.example.agent.core.blocker;

public class HookResult {

    private final boolean allowed;
    private final String reason;
    private final String suggestion;

    private HookResult(boolean allowed, String reason, String suggestion) {
        this.allowed = allowed;
        this.reason = reason;
        this.suggestion = suggestion;
    }

    public static HookResult allow() {
        return new HookResult(true, null, null);
    }

    public static HookResult deny(String reason, String suggestion) {
        return new HookResult(false, reason, suggestion);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String formatErrorMessage() {
        if (allowed) {
            return "";
        }
        return String.format("""
            ⛔ 执行被阻断
            ❌ 原因: %s
            💡 建议: %s
            """, reason, suggestion);
    }
}
