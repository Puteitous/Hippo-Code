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

    public static HookResult validationError(String reason, String example) {
        return new HookResult(false, reason, example);
    }

    /**
     * 逻辑/状态错误专用 - 只报错误码，不给指导
     * 所有 Blocker 的默认选择
     */
    public static HookResult block(String errorCode) {
        return new HookResult(false, errorCode, null);
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
        if (suggestion != null) {
            return String.format("""
                ⛔ 执行被阻断
                ❌ 原因: %s
                💡 示例: %s
                """, reason, suggestion);
        }
        return String.format("""
            ⛔ 执行被阻断
            ❌ %s
            """, reason);
    }
}
