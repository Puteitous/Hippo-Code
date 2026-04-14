package com.example.agent.core.error;

public enum ErrorCode {

    FILE_NOT_FOUND("F001", "文件不存在", "检查路径拼写，确认文件是否存在"),
    FILE_PERMISSION_ERROR("F002", "文件权限不足", "检查文件读写权限"),
    FILE_TOO_LARGE("F003", "文件过大", "考虑分片读取或使用搜索工具"),
    FILE_READ_ERROR("F004", "文件读取失败", "检查文件是否被其他程序占用"),
    FILE_WRITE_ERROR("F005", "文件写入失败", "检查磁盘空间或文件锁"),

    LLM_CONNECTION_ERROR("L001", "LLM 连接失败", "检查网络连接或 API 地址"),
    LLM_TIMEOUT("L002", "LLM 请求超时", "请稍后重试，或减少上下文长度"),
    LLM_AUTH_ERROR("L003", "LLM 认证失败", "检查 API Key 是否正确"),
    LLM_RATE_LIMIT("L004", "LLM 调用频率超限", "请稍后重试"),

    SYSTEM_OUT_OF_MEMORY("S001", "系统内存不足", "关闭其他程序或减少并发数"),
    SYSTEM_DISK_FULL("S002", "磁盘空间不足", "清理磁盘空间"),
    SYSTEM_CONFIG_ERROR("S003", "系统配置错误", "检查配置文件"),

    BLOCKED_EDIT_COUNT("B001", "编辑次数过多", "停止打补丁，先理解根本原因"),
    BLOCKED_DANGEROUS_COMMAND("B002", "危险命令被拦截", "该命令可能对系统造成危害");

    private final String code;
    private final String message;
    private final String suggestion;

    ErrorCode(String code, String message, String suggestion) {
        this.code = code;
        this.message = message;
        this.suggestion = suggestion;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
