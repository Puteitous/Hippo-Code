package com.example.agent.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class WorkspaceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);
    
    public static final Path HIPPO_ROOT = Paths.get(".hippo");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private static final String CURRENT_PROJECT_KEY = sanitizePath(getCurrentWorkingDir());
    
    static {
        ensureCoreDirectories();
    }
    
    private WorkspaceManager() {}
    
    public static String sanitizePath(String path) {
        String safe = path.replaceAll("[^a-zA-Z0-9]", "-");
        if (safe.length() > 64) {
            int hash = Math.abs(path.hashCode());
            return safe.substring(0, 48) + "-" + hash;
        }
        return safe;
    }
    
    public static String getCurrentWorkingDir() {
        return System.getProperty("user.dir");
    }
    
    public static String getCurrentProjectKey() {
        return CURRENT_PROJECT_KEY;
    }
    
    private static void ensureCoreDirectories() {
        try {
            Path[] dirs = {
                getProjectsRoot(),
                getGlobalConfigDir(),
                getGlobalMetricsDir(),
                getGlobalDebugDir(),
                getGlobalCacheDir()
            };
            for (Path dir : dirs) {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                    logger.debug("创建工作空间目录: {}", dir);
                }
            }
            logger.info("✅ 工作空间初始化完成，当前项目: {}", getCurrentWorkingDir());
        } catch (Exception e) {
            logger.error("❌ 工作空间初始化失败", e);
        }
    }
    
    public static void ensureProjectDirectories(String projectKey) {
        try {
            Path projectDir = getProjectDir(projectKey);
            Path[] dirs = {
                projectDir.resolve("sessions"),
                projectDir.resolve("resources"),
                projectDir.resolve("cache")
            };
            for (Path dir : dirs) {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
            }
            writeProjectMetadata(projectKey);
        } catch (Exception e) {
            logger.error("初始化项目目录失败: {}", projectKey, e);
        }
    }
    
    private static void writeProjectMetadata(String projectKey) {
        Path metaFile = getProjectDir(projectKey).resolve("project.json");
        if (!Files.exists(metaFile)) {
            try {
                String json = String.format("""
                    {
                      "originalPath": "%s",
                      "firstSeen": "%s",
                      "lastActive": "%s",
                      "sessionCount": 0
                    }
                    """,
                    getCurrentWorkingDir().replace("\\", "\\\\"),
                    LocalDate.now(),
                    LocalDate.now()
                );
                Files.writeString(metaFile, json);
            } catch (Exception e) {
                logger.debug("写入项目元数据失败: {}", e.getMessage());
            }
        }
    }
    
    public static Path getProjectsRoot() {
        return HIPPO_ROOT.resolve("projects");
    }
    
    public static Path getProjectDir(String projectKey) {
        return getProjectsRoot().resolve(projectKey);
    }
    
    public static Path getCurrentProjectDir() {
        return getProjectDir(CURRENT_PROJECT_KEY);
    }
    
    public static Path getSessionDir(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve("resources").resolve(sessionId);
    }
    
    public static Path getSessionMessagesFile(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve("sessions").resolve(sessionId + ".jsonl");
    }
    
    public static Path getSessionLogFile(String projectKey, String sessionId) {
        return getProjectDir(projectKey).resolve("sessions").resolve(sessionId + ".log");
    }
    
    public static Path getToolResultPath(String projectKey, String sessionId, String toolCallId) {
        return getSessionDir(projectKey, sessionId).resolve("tool-results").resolve(toolCallId + ".json");
    }
    
    public static Path getSessionPlanPath(String projectKey, String sessionId, String planId) {
        return getSessionDir(projectKey, sessionId).resolve("plans").resolve(planId + ".md");
    }
    
    public static Path getSubagentSessionPath(String projectKey, String sessionId, String agentId) {
        return getSessionDir(projectKey, sessionId).resolve("subagents").resolve("agent-" + agentId + ".jsonl");
    }
    
    public static Path getProjectCacheDir(String projectKey) {
        return getProjectDir(projectKey).resolve("cache");
    }
    
    public static Path getGlobalConfigDir() {
        return HIPPO_ROOT.resolve("config");
    }
    
    public static Path getGlobalMetricsDir() {
        return HIPPO_ROOT.resolve("metrics");
    }
    
    public static Path getGlobalDebugDir() {
        return HIPPO_ROOT.resolve("debug");
    }
    
    public static Path getGlobalCacheDir() {
        return HIPPO_ROOT.resolve("cache");
    }
    
    public static Path getUserRulesDir() {
        return HIPPO_ROOT.resolve("rules");
    }
    
    public static Path getUserSkillsDir() {
        return HIPPO_ROOT.resolve("skills");
    }
    
    public static Path getUserMemoryDir() {
        return HIPPO_ROOT.resolve("memory");
    }
    
    public static Path getTokenMetricsFile(LocalDate date) {
        return getGlobalMetricsDir().resolve("tokens_" + date.format(DATE_FORMAT) + ".csv");
    }
    
    public static Path getToolMetricsFile(LocalDate date) {
        return getGlobalMetricsDir().resolve("tools_" + date.format(DATE_FORMAT) + ".csv");
    }
    
    public static Path getDebugLogFile(String sessionId) {
        return getGlobalDebugDir().resolve(sessionId + ".txt");
    }
    
    public static void ensureSessionResources(String projectKey, String sessionId) {
        try {
            Path sessionDir = getSessionDir(projectKey, sessionId);
            Path[] dirs = {
                sessionDir.resolve("tool-results"),
                sessionDir.resolve("plans"),
                sessionDir.resolve("subagents")
            };
            for (Path dir : dirs) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            logger.error("初始化会话资源目录失败: {}", sessionId, e);
        }
    }
    
    @Deprecated
    public static Path getLegacyConversationLogFile(String sessionId, LocalDate date) {
        return Paths.get("logs").resolve("conversations")
                    .resolve(date.format(DATE_FORMAT))
                    .resolve("conv_" + sessionId + ".log");
    }
}