package com.example.agent.core.blocker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookResultTest {

    @Test
    void allow_shouldCreateAllowedResult() {
        HookResult result = HookResult.allow();
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getSuggestion());
        assertEquals("", result.formatErrorMessage());
    }

    @Test
    void deny_shouldCreateDeniedResultWithReasonAndSuggestion() {
        HookResult result = HookResult.deny("测试原因", "测试建议");

        assertFalse(result.isAllowed());
        assertEquals("测试原因", result.getReason());
        assertEquals("测试建议", result.getSuggestion());

        String errorMessage = result.formatErrorMessage();
        assertTrue(errorMessage.contains("执行被阻断"));
        assertTrue(errorMessage.contains("测试原因"));
        assertTrue(errorMessage.contains("测试建议"));
    }
}
