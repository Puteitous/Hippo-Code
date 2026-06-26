package com.example.agent.desktop.bridge;

import com.example.agent.desktop.WindowManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.*;

/**
 * 窗口控制 Bridge Handler — 处理前端发起的窗口控制请求。
 *
 * <p>
 * 通过构造函数注入 WindowManager，所有窗口操作委托给 WindowManager 实例方法。
 * 避免 DesktopApplication 持有窗口控制逻辑。
 * </p>
 */
public class WindowHandler extends CefMessageRouterHandlerAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WindowManager windowManager;

    public WindowHandler(WindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                           String request, boolean persistent, CefQueryCallback callback) {
        try {
            JsonNode json = MAPPER.readTree(request);
            String action = json.has("action") ? json.get("action").asText() : "";

            switch (action) {
                case "windowMinimize":
                    SwingUtilities.invokeLater(windowManager::minimizeWindow);
                    callback.success("{}");
                    return true;
                case "windowMaximize":
                    SwingUtilities.invokeLater(windowManager::maximizeWindow);
                    callback.success("{}");
                    return true;
                case "windowRestore":
                    SwingUtilities.invokeLater(windowManager::restoreWindow);
                    callback.success("{}");
                    return true;
                case "windowToggleMaximize":
                    SwingUtilities.invokeLater(windowManager::toggleMaximize);
                    callback.success("{}");
                    return true;
                case "windowClose":
                    SwingUtilities.invokeLater(windowManager::closeWindow);
                    callback.success("{}");
                    return true;
                case "windowIsMaximized":
                    callback.success(MAPPER.writeValueAsString(
                            MAPPER.createObjectNode().put("maximized", windowManager.isMaximized())));
                    return true;
                case "windowMove":
                    if (json.has("x") && json.has("y")) {
                        int wx = json.get("x").asInt();
                        int wy = json.get("y").asInt();
                        SwingUtilities.invokeLater(() -> windowManager.moveWindow(wx, wy));
                    }
                    callback.success("{}");
                    return true;
                case "windowResize":
                    if (json.has("x") && json.has("y") && json.has("width") && json.has("height")) {
                        int wx = json.get("x").asInt();
                        int wy = json.get("y").asInt();
                        int ww = json.get("width").asInt();
                        int wh = json.get("height").asInt();
                        SwingUtilities.invokeLater(() -> windowManager.resizeWindow(wx, wy, ww, wh));
                    }
                    callback.success("{}");
                    return true;
                case "windowGetState":
                    // 需要在 EDT 读取 JFrame 状态，避免非 EDT 线程访问 Swing 组件
                    try {
                        final com.fasterxml.jackson.databind.node.ObjectNode[] stateHolder =
                                new com.fasterxml.jackson.databind.node.ObjectNode[1];
                        SwingUtilities.invokeAndWait(() ->
                                stateHolder[0] = windowManager.getState(MAPPER));
                        callback.success(MAPPER.writeValueAsString(stateHolder[0]));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        callback.failure(500, "Interrupted while reading window state");
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        callback.failure(500, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    }
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            // 不在这里处理未知错误，交给下一个 handler
            return false;
        }
    }
}
