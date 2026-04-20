package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class SessionMemoryManager {

    private static final String SESSION_DIR = ".hippo/sessions";
    private static final String MEMORY_FILE = "session-memory.md";

    private final String sessionId;
    private final Path sessionPath;
    private final Path memoryFilePath;

    public SessionMemoryManager(String sessionId) {
        this.sessionId = sessionId;
        this.sessionPath = Paths.get(System.getProperty("user.dir"), SESSION_DIR, sessionId);
        this.memoryFilePath = sessionPath.resolve(MEMORY_FILE);
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
            Files.createDirectories(sessionPath);
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

    public static String getDefaultMemoryTemplate() {
        return "# Session Memory\n\n" +
            "## 关键决策\n\n" +
            "## 错误与修复\n\n" +
            "## 当前进度\n\n" +
            "---\n" +
            "> 本文件由 BackgroundExtractor 自动维护\n";
    }
}
