package com.example.agent.memory;

import com.example.agent.logging.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SessionMemoryManager {

    private static final String MEMORY_FILE = "session-memory.md";

    private final String sessionId;
    private final Path memoryFilePath;

    public SessionMemoryManager(String sessionId) {
        this.sessionId = sessionId;
        String projectKey = WorkspaceManager.getCurrentProjectKey();
        this.memoryFilePath = WorkspaceManager.getSessionMemoryPath(projectKey, sessionId);
        ensureDirectory();
    }

    public String read() {
        if (!Files.exists(memoryFilePath)) {
            return null;
        }
        try {
            return Files.readString(memoryFilePath);
        } catch (IOException e) {
            return null;
        }
    }

    public void write(String content) {
        if (content == null) {
            return;
        }
        ensureDirectory();
        try {
            Files.writeString(
                memoryFilePath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to write session memory", e);
        }
    }

    public void append(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ensureDirectory();
        try {
            String existing = read();
            if (existing == null) {
                write(content);
            } else {
                write(existing + "\n\n---\n\n" + content);
            }
        } catch (Exception e) {
        }
    }

    public boolean exists() {
        return Files.exists(memoryFilePath);
    }

    public void clear() {
        try {
            Files.deleteIfExists(memoryFilePath);
        } catch (IOException e) {
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(memoryFilePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session directory", e);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public Path getMemoryFilePath() {
        return memoryFilePath;
    }

    public boolean hasActualContent() {
        String content = read();
        if (content == null || content.isBlank()) {
            return false;
        }
        String template = getDefaultMemoryTemplate();
        return !content.trim().equals(template.trim());
    }

    public void initializeIfNotExists() {
        if (!exists()) {
            write(getDefaultMemoryTemplate());
        }
    }

    public static String getDefaultMemoryTemplate() {
        return "# Session Title\n" +
            "_用 5-10 个词概括这个会话的主题_\n\n" +
            "---\n\n" +
            "# Current State\n" +
            "_当前正在做什么、待完成的任务、明确的下一步计划_\n\n" +
            "---\n\n" +
            "# Task Specification\n" +
            "_用户的核心需求、设计决策、约定的实现方案_\n\n" +
            "---\n\n" +
            "# Files and Functions\n" +
            "_关键文件路径、核心函数、相关性说明_\n\n" +
            "---\n\n" +
            "# Workflow\n" +
            "_Bash 命令、执行顺序、关键输出解读_\n\n" +
            "---\n\n" +
            "# Errors & Corrections\n" +
            "_遇到的问题、修复方案、用户纠正、失败尝试_\n\n" +
            "---\n\n" +
            "# Codebase Documentation\n" +
            "_系统架构、组件关系、工作原理_\n\n" +
            "---\n\n" +
            "# Learnings\n" +
            "_有效/无效的方法、避坑指南、经验教训_\n\n" +
            "---\n\n" +
            "# Key Results\n" +
            "_用户要求的具体产出：答案、表格、数据_\n\n" +
            "---\n\n" +
            "# Worklog\n" +
            "_按时间顺序的简洁工作记录，每步一行_\n\n" +
            "---\n\n" +
            "> 本文件由 Memory Extractor Sub-Agent 自动维护\n";
    }
}
