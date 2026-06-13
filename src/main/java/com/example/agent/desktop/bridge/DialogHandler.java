package com.example.agent.desktop.bridge;

import com.example.agent.desktop.NativeFolderPicker;
import com.example.agent.desktop.WorkspaceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * 系统对话框处理 — 打开文件夹选择对话框。
 *
 * <p>
 * 通过构造函数注入父窗口 JFrame，在独立线程中运行以避免阻塞 CEF 线程，
 * 选择结果通过 JS callback 返回给前端。
 * </p>
 */
public class DialogHandler extends CefMessageRouterHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DialogHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JFrame parentFrame;

    public DialogHandler(JFrame parentFrame) {
        this.parentFrame = parentFrame;
    }

    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                           String request, boolean persistent, CefQueryCallback callback) {
        if (!request.contains("openFileDialog")) {
            return false;
        }
        handleOpenFileDialog(browser, callback);
        return true;
    }

    private void handleOpenFileDialog(CefBrowser browser, CefQueryCallback callback) {
        // 立即确认请求，CEF 不会超时或清理回调
        callback.success("{\"status\":\"pending\"}");
        // 在普通工作线程上运行，避免阻塞 CEF 线程
        new Thread(() -> {
            try {
                String[] pathHolder = new String[1];
                SwingUtilities.invokeAndWait(() -> {
                    pathHolder[0] = NativeFolderPicker.chooseFolder(parentFrame);
                });
                String path = pathHolder[0];

                final String selectedPath = (path != null && !path.isBlank()) ? path : null;
                if (selectedPath != null) {
                    SwingUtilities.invokeAndWait(() -> {
                        WorkspaceContext.setCurrentFolder(selectedPath);
                        WorkspaceContext.save();
                    });
                }

                ObjectNode result = MAPPER.createObjectNode();
                if (selectedPath != null) {
                    result.put("path", selectedPath);
                } else {
                    result.putNull("path");
                }
                String js = "window._onOpenFolderResult(" + MAPPER.writeValueAsString(result) + ")";
                SwingUtilities.invokeLater(() -> browser.executeJavaScript(js, "", 0));
            } catch (Exception e) {
                logger.error("openFileDialog failed", e);
                SwingUtilities.invokeLater(() ->
                        browser.executeJavaScript("window._onOpenFolderResult({\"path\":null})", "", 0));
            }
        }, "open-file-dialog").start();
    }
}
