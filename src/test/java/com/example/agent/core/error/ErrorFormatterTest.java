package com.example.agent.core.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("错误格式化器测试")
class ErrorFormatterTest {

    @Test
    @DisplayName("format 方法格式化异常消息")
    void testFormat() {
        AgentException exception = new AgentException(
            ErrorCode.FILE_NOT_FOUND,
            "测试错误"
        );

        String result = ErrorFormatter.format(exception);

        assertTrue(result.contains("测试错误"));
        assertTrue(result.contains("💡 建议："));
        assertTrue(result.contains("检查路径拼写，确认文件是否存在"));
    }

    @Test
    @DisplayName("formatBlocked 方法格式化阻断消息")
    void testFormatBlocked() {
        String reason = "测试阻断原因";
        String result = ErrorFormatter.formatBlocked(reason);

        assertTrue(result.contains("[BLOCKED]"));
        assertTrue(result.contains("操作被阻断"));
        assertTrue(result.contains("原因：测试阻断原因"));
    }

    @Test
    @DisplayName("formatForAi 方法格式化 AI 消息（无详情）")
    void testFormatForAiWithoutDetail() {
        AgentException exception = new AgentException(
            ErrorCode.LLM_TIMEOUT,
            "LLM 超时"
        );

        String result = ErrorFormatter.formatForAi(exception);

        assertEquals("LLM_TIMEOUT", result);
    }

    @Test
    @DisplayName("formatForAi 方法格式化 AI 消息（有详情）")
    void testFormatForAiWithDetail() {
        AgentException exception = new AgentException(
            ErrorCode.FILE_READ_ERROR,
            "文件读取失败",
            new RuntimeException("详细信息：文件被占用")
        );

        String result = ErrorFormatter.formatForAi(exception);

        assertTrue(result.contains("FILE_READ_ERROR"));
        assertTrue(result.contains("详细信息：文件被占用"));
    }

    @Test
    @DisplayName("format 方法包含完整的错误信息和建议")
    void testFormatCompleteness() {
        AgentException exception = new AgentException(
            ErrorCode.BLOCKED_DANGEROUS_COMMAND,
            "危险命令"
        );

        String result = ErrorFormatter.format(exception);

        assertTrue(result.contains("危险命令"));
        assertTrue(result.contains("该命令可能对系统造成危害"));
    }
}
