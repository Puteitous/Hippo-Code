package com.example.agent.core.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("错误码消息测试")
class ErrorCodeMessagesTest {

    @Nested
    @DisplayName("文件错误消息测试")
    class FileErrorTests {

        @Test
        @DisplayName("FILE_NOT_FOUND 消息正确")
        void testFileNotFound() {
            assertEquals("文件不存在", ErrorCodeMessages.getMessage(ErrorCode.FILE_NOT_FOUND));
            assertEquals("检查路径拼写，确认文件是否存在", ErrorCodeMessages.getSuggestion(ErrorCode.FILE_NOT_FOUND));
        }

        @Test
        @DisplayName("FILE_PERMISSION_ERROR 消息正确")
        void testFilePermissionError() {
            assertEquals("文件权限不足", ErrorCodeMessages.getMessage(ErrorCode.FILE_PERMISSION_ERROR));
            assertEquals("检查文件读写权限", ErrorCodeMessages.getSuggestion(ErrorCode.FILE_PERMISSION_ERROR));
        }

        @Test
        @DisplayName("FILE_TOO_LARGE 消息正确")
        void testFileTooLarge() {
            assertEquals("文件过大", ErrorCodeMessages.getMessage(ErrorCode.FILE_TOO_LARGE));
            assertEquals("考虑分片读取或使用搜索工具", ErrorCodeMessages.getSuggestion(ErrorCode.FILE_TOO_LARGE));
        }

        @Test
        @DisplayName("FILE_READ_ERROR 消息正确")
        void testFileReadError() {
            assertEquals("文件读取失败", ErrorCodeMessages.getMessage(ErrorCode.FILE_READ_ERROR));
            assertEquals("检查文件是否被其他程序占用", ErrorCodeMessages.getSuggestion(ErrorCode.FILE_READ_ERROR));
        }

        @Test
        @DisplayName("FILE_WRITE_ERROR 消息正确")
        void testFileWriteError() {
            assertEquals("文件写入失败", ErrorCodeMessages.getMessage(ErrorCode.FILE_WRITE_ERROR));
            assertEquals("检查磁盘空间或文件锁", ErrorCodeMessages.getSuggestion(ErrorCode.FILE_WRITE_ERROR));
        }
    }

    @Nested
    @DisplayName("LLM 错误消息测试")
    class LlmErrorTests {

        @Test
        @DisplayName("LLM_CONNECTION_ERROR 消息正确")
        void testLlmConnectionError() {
            assertEquals("LLM 连接失败", ErrorCodeMessages.getMessage(ErrorCode.LLM_CONNECTION_ERROR));
            assertEquals("检查网络连接或 API 地址", ErrorCodeMessages.getSuggestion(ErrorCode.LLM_CONNECTION_ERROR));
        }

        @Test
        @DisplayName("LLM_TIMEOUT 消息正确")
        void testLlmTimeout() {
            assertEquals("LLM 请求超时", ErrorCodeMessages.getMessage(ErrorCode.LLM_TIMEOUT));
            assertEquals("请稍后重试，或减少上下文长度", ErrorCodeMessages.getSuggestion(ErrorCode.LLM_TIMEOUT));
        }

        @Test
        @DisplayName("LLM_AUTH_ERROR 消息正确")
        void testLlmAuthError() {
            assertEquals("LLM 认证失败", ErrorCodeMessages.getMessage(ErrorCode.LLM_AUTH_ERROR));
            assertEquals("检查 API Key 是否正确", ErrorCodeMessages.getSuggestion(ErrorCode.LLM_AUTH_ERROR));
        }

        @Test
        @DisplayName("LLM_RATE_LIMIT 消息正确")
        void testLlmRateLimit() {
            assertEquals("LLM 调用频率超限", ErrorCodeMessages.getMessage(ErrorCode.LLM_RATE_LIMIT));
            assertEquals("请稍后重试", ErrorCodeMessages.getSuggestion(ErrorCode.LLM_RATE_LIMIT));
        }
    }

    @Nested
    @DisplayName("系统错误消息测试")
    class SystemErrorTests {

        @Test
        @DisplayName("SYSTEM_OUT_OF_MEMORY 消息正确")
        void testSystemOutOfMemory() {
            assertEquals("系统内存不足", ErrorCodeMessages.getMessage(ErrorCode.SYSTEM_OUT_OF_MEMORY));
            assertEquals("关闭其他程序或减少并发数", ErrorCodeMessages.getSuggestion(ErrorCode.SYSTEM_OUT_OF_MEMORY));
        }

        @Test
        @DisplayName("SYSTEM_DISK_FULL 消息正确")
        void testSystemDiskFull() {
            assertEquals("磁盘空间不足", ErrorCodeMessages.getMessage(ErrorCode.SYSTEM_DISK_FULL));
            assertEquals("清理磁盘空间", ErrorCodeMessages.getSuggestion(ErrorCode.SYSTEM_DISK_FULL));
        }

        @Test
        @DisplayName("SYSTEM_CONFIG_ERROR 消息正确")
        void testSystemConfigError() {
            assertEquals("系统配置错误", ErrorCodeMessages.getMessage(ErrorCode.SYSTEM_CONFIG_ERROR));
            assertEquals("检查配置文件", ErrorCodeMessages.getSuggestion(ErrorCode.SYSTEM_CONFIG_ERROR));
        }
    }

    @Nested
    @DisplayName("阻断错误消息测试")
    class BlockedErrorTests {

        @Test
        @DisplayName("BLOCKED_EDIT_COUNT 消息正确")
        void testBlockedEditCount() {
            assertEquals("编辑次数过多", ErrorCodeMessages.getMessage(ErrorCode.BLOCKED_EDIT_COUNT));
            assertEquals("停止打补丁，先理解根本原因", ErrorCodeMessages.getSuggestion(ErrorCode.BLOCKED_EDIT_COUNT));
        }

        @Test
        @DisplayName("BLOCKED_DANGEROUS_COMMAND 消息正确")
        void testBlockedDangerousCommand() {
            assertEquals("危险命令被拦截", ErrorCodeMessages.getMessage(ErrorCode.BLOCKED_DANGEROUS_COMMAND));
            assertEquals("该命令可能对系统造成危害", ErrorCodeMessages.getSuggestion(ErrorCode.BLOCKED_DANGEROUS_COMMAND));
        }
    }
}
