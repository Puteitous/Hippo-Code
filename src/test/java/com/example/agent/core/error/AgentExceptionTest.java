package com.example.agent.core.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentException 异常类测试")
class AgentExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("使用 ErrorCode 构造异常")
        void testConstructorWithErrorCode() {
            AgentException exception = new AgentException(ErrorCode.FILE_NOT_FOUND);

            assertEquals(ErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
            assertNull(exception.getDetail());
            assertTrue(exception.getMessage().contains("F001"));
            assertTrue(exception.getMessage().contains("文件不存在"));
        }

        @Test
        @DisplayName("使用 ErrorCode 和 detail 构造异常")
        void testConstructorWithErrorCodeAndDetail() {
            String detail = "详细错误信息";
            AgentException exception = new AgentException(ErrorCode.LLM_TIMEOUT, detail);

            assertEquals(ErrorCode.LLM_TIMEOUT, exception.getErrorCode());
            assertEquals(detail, exception.getDetail());
            assertTrue(exception.getMessage().contains("L002"));
            assertTrue(exception.getMessage().contains("LLM 请求超时"));
            assertTrue(exception.getMessage().contains(detail));
        }

        @Test
        @DisplayName("使用 ErrorCode、detail 和 cause 构造异常")
        void testConstructorWithCause() {
            Throwable cause = new RuntimeException("根本原因");
            AgentException exception = new AgentException(
                ErrorCode.FILE_WRITE_ERROR,
                "写入失败详情",
                cause
            );

            assertEquals(ErrorCode.FILE_WRITE_ERROR, exception.getErrorCode());
            assertEquals("写入失败详情", exception.getDetail());
            assertEquals(cause, exception.getCause());
        }
    }

    @Nested
    @DisplayName("消息格式化测试")
    class MessageFormatTests {

        @Test
        @DisplayName("无 detail 时的消息格式")
        void testMessageFormatWithoutDetail() {
            AgentException exception = new AgentException(ErrorCode.SYSTEM_CONFIG_ERROR);

            String message = exception.getMessage();
            assertTrue(message.startsWith("[S003]"));
            assertTrue(message.contains("系统配置错误"));
            assertFalse(message.contains(": "));
        }

        @Test
        @DisplayName("有 detail 时的消息格式")
        void testMessageFormatWithDetail() {
            AgentException exception = new AgentException(
                ErrorCode.BLOCKED_EDIT_COUNT,
                "已编辑 10 次"
            );

            String message = exception.getMessage();
            assertTrue(message.contains("[B001]"));
            assertTrue(message.contains("编辑次数过多"));
            assertTrue(message.contains(": 已编辑 10 次"));
        }

        @Test
        @DisplayName("空 detail 被视为 null")
        void testMessageFormatWithEmptyDetail() {
            AgentException exception = new AgentException(
                ErrorCode.LLM_AUTH_ERROR,
                ""
            );

            String message = exception.getMessage();
            assertTrue(message.contains("[L003]"));
            assertTrue(message.contains("LLM 认证失败"));
            assertFalse(message.contains(": "));
        }
    }

    @Nested
    @DisplayName("getSuggestion 方法测试")
    class SuggestionTests {

        @Test
        @DisplayName("获取 FILE_NOT_FOUND 的建议")
        void testGetSuggestionForFileNotFound() {
            AgentException exception = new AgentException(ErrorCode.FILE_NOT_FOUND);
            assertEquals("检查路径拼写，确认文件是否存在", exception.getSuggestion());
        }

        @Test
        @DisplayName("获取 LLM_CONNECTION_ERROR 的建议")
        void testGetSuggestionForLlmConnectionError() {
            AgentException exception = new AgentException(ErrorCode.LLM_CONNECTION_ERROR);
            assertEquals("检查网络连接或 API 地址", exception.getSuggestion());
        }

        @Test
        @DisplayName("获取 BLOCKED_DANGEROUS_COMMAND 的建议")
        void testGetSuggestionForBlockedCommand() {
            AgentException exception = new AgentException(ErrorCode.BLOCKED_DANGEROUS_COMMAND);
            assertEquals("该命令可能对系统造成危害", exception.getSuggestion());
        }
    }

    @Nested
    @DisplayName("异常继承测试")
    class InheritanceTests {

        @Test
        @DisplayName("AgentException 是 RuntimeException")
        void testIsRuntimeException() {
            AgentException exception = new AgentException(ErrorCode.FILE_READ_ERROR);
            assertTrue(exception instanceof RuntimeException);
        }

        @Test
        @DisplayName("可以作为 RuntimeException 捕获")
        void testCanBeCaughtAsRuntimeException() {
            try {
                throw new AgentException(ErrorCode.SYSTEM_OUT_OF_MEMORY);
            } catch (RuntimeException e) {
                assertNotNull(e);
                assertTrue(e instanceof AgentException);
            }
        }
    }
}
