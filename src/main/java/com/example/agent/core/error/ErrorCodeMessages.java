package com.example.agent.core.error;

import java.util.EnumMap;
import java.util.Map;

class ErrorCodeMessages {

    private static final Map<ErrorCode, String> messages = new EnumMap<>(ErrorCode.class);
    private static final Map<ErrorCode, String> suggestions = new EnumMap<>(ErrorCode.class);

    static {
        messages.put(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        messages.put(ErrorCode.FILE_PERMISSION_ERROR, "文件权限不足");
        messages.put(ErrorCode.FILE_TOO_LARGE, "文件过大");
        messages.put(ErrorCode.FILE_READ_ERROR, "文件读取失败");
        messages.put(ErrorCode.FILE_WRITE_ERROR, "文件写入失败");

        messages.put(ErrorCode.LLM_CONNECTION_ERROR, "LLM 连接失败");
        messages.put(ErrorCode.LLM_TIMEOUT, "LLM 请求超时");
        messages.put(ErrorCode.LLM_AUTH_ERROR, "LLM 认证失败");
        messages.put(ErrorCode.LLM_RATE_LIMIT, "LLM 调用频率超限");

        messages.put(ErrorCode.SYSTEM_OUT_OF_MEMORY, "系统内存不足");
        messages.put(ErrorCode.SYSTEM_DISK_FULL, "磁盘空间不足");
        messages.put(ErrorCode.SYSTEM_CONFIG_ERROR, "系统配置错误");

        messages.put(ErrorCode.BLOCKED_EDIT_COUNT, "编辑次数过多");
        messages.put(ErrorCode.BLOCKED_DANGEROUS_COMMAND, "危险命令被拦截");
    }

    static {
        suggestions.put(ErrorCode.FILE_NOT_FOUND, "检查路径拼写，确认文件是否存在");
        suggestions.put(ErrorCode.FILE_PERMISSION_ERROR, "检查文件读写权限");
        suggestions.put(ErrorCode.FILE_TOO_LARGE, "考虑分片读取或使用搜索工具");
        suggestions.put(ErrorCode.FILE_READ_ERROR, "检查文件是否被其他程序占用");
        suggestions.put(ErrorCode.FILE_WRITE_ERROR, "检查磁盘空间或文件锁");

        suggestions.put(ErrorCode.LLM_CONNECTION_ERROR, "检查网络连接或 API 地址");
        suggestions.put(ErrorCode.LLM_TIMEOUT, "请稍后重试，或减少上下文长度");
        suggestions.put(ErrorCode.LLM_AUTH_ERROR, "检查 API Key 是否正确");
        suggestions.put(ErrorCode.LLM_RATE_LIMIT, "请稍后重试");

        suggestions.put(ErrorCode.SYSTEM_OUT_OF_MEMORY, "关闭其他程序或减少并发数");
        suggestions.put(ErrorCode.SYSTEM_DISK_FULL, "清理磁盘空间");
        suggestions.put(ErrorCode.SYSTEM_CONFIG_ERROR, "检查配置文件");

        suggestions.put(ErrorCode.BLOCKED_EDIT_COUNT, "停止打补丁，先理解根本原因");
        suggestions.put(ErrorCode.BLOCKED_DANGEROUS_COMMAND, "该命令可能对系统造成危害");
    }

    static String getMessage(ErrorCode code) {
        return messages.get(code);
    }

    static String getSuggestion(ErrorCode code) {
        return suggestions.get(code);
    }
}
