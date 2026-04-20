package com.example.agent.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @deprecated 已废弃，请使用 WorkspaceManager 替代
 * 此类仅用于向后兼容，将在 v1.0 移除
 */
@Deprecated
public class LogDirectoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(LogDirectoryManager.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /** @deprecated */
    @Deprecated
    public static Path getLogRoot() {
        return WorkspaceManager.HIPPO_ROOT;
    }
    
    /** @deprecated */
    @Deprecated
    public static Path getSystemLogDir() {
        return WorkspaceManager.getGlobalDebugDir();
    }
    
    /** @deprecated */
    @Deprecated
    public static Path getConversationLogDir(LocalDate date) {
        return WorkspaceManager.getCurrentProjectDir().resolve("sessions");
    }
    
    /** @deprecated */
    @Deprecated
    public static Path getMetricsDir() {
        return WorkspaceManager.getGlobalMetricsDir();
    }
    
    /** @deprecated */
    @Deprecated
    public static Path getConversationLogFile(String conversationId, LocalDate date) {
        return WorkspaceManager.getSessionLogFile(WorkspaceManager.getCurrentProjectKey(), conversationId);
    }
    
    /** @deprecated */
    @Deprecated
    public static Path getTokenMetricsFile(LocalDate date) {
        return WorkspaceManager.getTokenMetricsFile(date);
    }
    
    /** @deprecated */
    @Deprecated
    public static Path getToolMetricsFile(LocalDate date) {
        return WorkspaceManager.getToolMetricsFile(date);
    }
    
    public static void ensureDirectoriesExist() {
        logger.warn("⚠️ LogDirectoryManager 已废弃，请使用 WorkspaceManager");
        // 委托给新的 WorkspaceManager
        String projectKey = WorkspaceManager.getCurrentProjectKey();
        WorkspaceManager.ensureProjectDirectories(projectKey);
    }
}
