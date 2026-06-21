package com.example.agent.desktop.bridge;

import com.example.agent.desktop.NativeFolderPicker;
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

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 系统对话框处理 — 打开文件夹选择对话框、文件保存对话框。
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
        try {
            JsonNode json = MAPPER.readTree(request);
            String action = json.has("action") ? json.get("action").asText() : "";

            switch (action) {
                case "openFileDialog":
                    handleOpenFileDialog(browser, callback);
                    return true;
                case "saveFileDialog":
                    handleSaveFileDialog(browser, json, callback);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.error("DialogHandler parse failed", e);
            return false;
        }
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

    /**
     * 文件保存对话框 — 接收 base64 内容，弹出系统另存为对话框，写入文件。
     * <p>
     * 完全绕过 CEF 下载机制，避免嵌入式 Chromium 对 blob URL 导航的兼容性问题。
     * </p>
     */
    private void handleSaveFileDialog(CefBrowser browser, JsonNode json, CefQueryCallback callback) {
        // 立即确认请求，CEF 不会超时或清理回调
        callback.success("{\"status\":\"pending\"}");

        String content = json.has("content") ? json.get("content").asText() : "";
        String suggestedName = json.has("suggestedName") ? json.get("suggestedName").asText() : "download";
        String mimeType = json.has("mimeType") ? json.get("mimeType").asText() : "application/octet-stream";

        if (content.isEmpty()) {
            SwingUtilities.invokeLater(() ->
                    browser.executeJavaScript("window._onSaveFileDialogResult({\"path\":null,\"error\":\"内容为空\"})", "", 0));
            return;
        }

        new Thread(() -> {
            try {
                // 解码 base64 内容
                byte[] fileBytes = Base64.getDecoder().decode(content);

                // 在 EDT 线程显示保存对话框
                final String[] selectedPath = new String[1];
                SwingUtilities.invokeAndWait(() -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setSelectedFile(new java.io.File(suggestedName));
                    chooser.setDialogTitle("保存文件");

                    // 根据 MIME 类型设置文件过滤器
                    String ext = suggestedName.contains(".")
                            ? suggestedName.substring(suggestedName.lastIndexOf('.') + 1)
                            : "";
                    if (!ext.isEmpty()) {
                        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                                mimeType.replace("image/", "").toUpperCase() + " 文件 (*." + ext + ")", ext);
                        chooser.setFileFilter(filter);
                    }

                    int result = chooser.showSaveDialog(parentFrame);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        selectedPath[0] = chooser.getSelectedFile().getAbsolutePath();
                    }
                });

                String path = selectedPath[0];
                if (path == null) {
                    // 用户取消
                    SwingUtilities.invokeLater(() ->
                            browser.executeJavaScript("window._onSaveFileDialogResult({\"path\":null})", "", 0));
                    return;
                }

                // 确保扩展名正确
                if (suggestedName.contains(".")) {
                    String expectedExt = suggestedName.substring(suggestedName.lastIndexOf('.'));
                    if (!path.toLowerCase().endsWith(expectedExt.toLowerCase())) {
                        path += expectedExt;
                    }
                }

                // 写入文件
                Path target = Paths.get(path);
                Files.createDirectories(target.getParent());
                Files.write(target, fileBytes);

                ObjectNode result = MAPPER.createObjectNode();
                result.put("path", target.toAbsolutePath().toString());
                result.put("size", fileBytes.length);
                String js = "window._onSaveFileDialogResult(" + MAPPER.writeValueAsString(result) + ")";
                SwingUtilities.invokeLater(() -> browser.executeJavaScript(js, "", 0));

                logger.info("文件已保存: {}", target.toAbsolutePath());
            } catch (IOException e) {
                logger.error("saveFileDialog 写入失败", e);
                SwingUtilities.invokeLater(() ->
                        browser.executeJavaScript("window._onSaveFileDialogResult({\"path\":null,\"error\":\"" +
                                e.getMessage().replace("\"", "\\\"") + "\"})", "", 0));
            } catch (Exception e) {
                logger.error("saveFileDialog 失败", e);
                SwingUtilities.invokeLater(() ->
                        browser.executeJavaScript("window._onSaveFileDialogResult({\"path\":null})", "", 0));
            }
        }, "save-file-dialog").start();
    }
}
