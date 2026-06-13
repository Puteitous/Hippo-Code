package com.example.agent.desktop.bridge;

import com.example.agent.desktop.WorkspaceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置持久化 Bridge Handler — 主题/最近文件夹/工作区会话/工作区路径。
 *
 * <p>
 * 无状态，通过构造函数注入配置文件的存储路径。
 * 这些路径由 WindowManager 提供，但 ConfigHandler 不依赖 WindowManager 类型，
 * 只依赖 Path 值，保持低耦合。
 * </p>
 */
public class ConfigHandler extends CefMessageRouterHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ConfigHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path themeConfigPath;
    private final Path recentFoldersConfigPath;
    private final Path workspaceSessionConfigPath;

    public ConfigHandler(Path themeConfigPath, Path recentFoldersConfigPath, Path workspaceSessionConfigPath) {
        this.themeConfigPath = themeConfigPath;
        this.recentFoldersConfigPath = recentFoldersConfigPath;
        this.workspaceSessionConfigPath = workspaceSessionConfigPath;
    }

    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                           String request, boolean persistent, CefQueryCallback callback) {
        try {
            JsonNode json = MAPPER.readTree(request);
            String action = json.has("action") ? json.get("action").asText() : "";

            switch (action) {
                case "getCurrentFolder":
                    handleGetCurrentFolder(callback);
                    return true;
                case "setCurrentFolder":
                    handleSetCurrentFolder(json, callback);
                    return true;
                case "clearCurrentFolder":
                    handleClearCurrentFolder(callback);
                    return true;
                case "getTheme":
                    handleGetTheme(callback);
                    return true;
                case "setTheme":
                    handleSetTheme(json, callback);
                    return true;
                case "getRecentFolders":
                    handleGetRecentFolders(callback);
                    return true;
                case "setRecentFolders":
                    handleSetRecentFolders(json, callback);
                    return true;
                case "getWorkspaceSession":
                    handleGetWorkspaceSession(callback);
                    return true;
                case "setWorkspaceSession":
                    handleSetWorkspaceSession(json, callback);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.error("ConfigHandler query failed", e);
            callback.failure(500, e.getMessage());
            return true;
        }
    }

    // ===== 工作区路径 =====

    private void handleGetCurrentFolder(CefQueryCallback callback) throws Exception {
        callback.success(MAPPER.writeValueAsString(
                MAPPER.createObjectNode().put("path", WorkspaceContext.getCurrentFolder() != null
                        ? WorkspaceContext.getCurrentFolder() : "")));
    }

    private void handleSetCurrentFolder(JsonNode json, CefQueryCallback callback) throws Exception {
        WorkspaceContext.setCurrentFolder(json.has("path") ? json.get("path").asText() : null);
        WorkspaceContext.save();
        callback.success(MAPPER.writeValueAsString(
                MAPPER.createObjectNode().put("path", WorkspaceContext.getCurrentFolder())));
    }

    private void handleClearCurrentFolder(CefQueryCallback callback) throws Exception {
        WorkspaceContext.clear();
        WorkspaceContext.save();
        callback.success(MAPPER.writeValueAsString(
                MAPPER.createObjectNode().put("path", "")));
    }

    // ===== 主题 =====

    private void handleGetTheme(CefQueryCallback callback) {
        try {
            String theme = "light";
            if (Files.exists(themeConfigPath)) {
                String saved = Files.readString(themeConfigPath).trim();
                if (saved.equals("dark") || saved.equals("light")) {
                    theme = saved;
                }
            }
            callback.success(MAPPER.writeValueAsString(
                    MAPPER.createObjectNode().put("theme", theme)));
        } catch (Exception e) {
            logger.error("读取主题配置失败", e);
            callback.success("{\"theme\":\"light\"}");
        }
    }

    private void handleSetTheme(JsonNode json, CefQueryCallback callback) {
        try {
            String theme = json.has("theme") ? json.get("theme").asText() : "light";
            if (!theme.equals("dark") && !theme.equals("light")) {
                theme = "light";
            }
            Files.createDirectories(themeConfigPath.getParent());
            Files.writeString(themeConfigPath, theme);
            logger.info("主题配置已保存: {}", theme);
            callback.success("{}");
        } catch (Exception e) {
            logger.error("保存主题配置失败", e);
            callback.failure(500, e.getMessage());
        }
    }

    // ===== 最近文件夹 =====

    private void handleGetRecentFolders(CefQueryCallback callback) {
        try {
            String data = "[]";
            if (Files.exists(recentFoldersConfigPath)) {
                data = Files.readString(recentFoldersConfigPath).trim();
            }
            callback.success(MAPPER.writeValueAsString(
                    MAPPER.createObjectNode().put("folders", data)));
        } catch (Exception e) {
            logger.error("读取最近文件夹配置失败", e);
            callback.success("{\"folders\":[]}");
        }
    }

    private void handleSetRecentFolders(JsonNode json, CefQueryCallback callback) {
        try {
            String folders = json.has("folders") ? json.get("folders").asText() : "[]";
            Files.createDirectories(recentFoldersConfigPath.getParent());
            Files.writeString(recentFoldersConfigPath, folders);
            logger.debug("最近文件夹列表已保存");
            callback.success("{}");
        } catch (Exception e) {
            logger.error("保存最近文件夹配置失败", e);
            callback.failure(500, e.getMessage());
        }
    }

    // ===== 工作区会话 =====

    private void handleGetWorkspaceSession(CefQueryCallback callback) {
        try {
            String data = "null";
            if (Files.exists(workspaceSessionConfigPath)) {
                data = Files.readString(workspaceSessionConfigPath).trim();
            }
            callback.success(MAPPER.writeValueAsString(
                    MAPPER.createObjectNode().put("session", data)));
        } catch (Exception e) {
            logger.error("读取工作区会话配置失败", e);
            callback.success("{\"session\":null}");
        }
    }

    private void handleSetWorkspaceSession(JsonNode json, CefQueryCallback callback) {
        try {
            String session = json.has("session") ? json.get("session").asText() : "null";
            Files.createDirectories(workspaceSessionConfigPath.getParent());
            if ("null".equals(session) || session.isEmpty()) {
                Files.deleteIfExists(workspaceSessionConfigPath);
            } else {
                Files.writeString(workspaceSessionConfigPath, session);
            }
            logger.debug("工作区会话已保存");
            callback.success("{}");
        } catch (Exception e) {
            logger.error("保存工作区会话失败", e);
            callback.failure(500, e.getMessage());
        }
    }
}
