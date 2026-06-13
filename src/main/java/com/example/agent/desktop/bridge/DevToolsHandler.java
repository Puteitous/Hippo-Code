package com.example.agent.desktop.bridge;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DevTools 窗口管理 — 打开/关闭/防重入/资源清理。
 *
 * <p>
 * 通过构造函数注入父窗口 JFrame，不依赖 DesktopApplication 静态字段。
 * 使用实例级 pendingCallbacks 持有异步回调引用。
 * </p>
 */
public class DevToolsHandler extends CefMessageRouterHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DevToolsHandler.class);

    private final JFrame parentFrame;
    private final List<CefQueryCallback> pendingCallbacks = new CopyOnWriteArrayList<>();

    private JDialog devToolsDialog;
    private CefBrowser devToolsBrowser;

    public DevToolsHandler(JFrame parentFrame) {
        this.parentFrame = parentFrame;
    }

    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                           String request, boolean persistent, CefQueryCallback callback) {
        if (!request.contains("openDevTools")) {
            return false;
        }
        handleOpenDevTools(browser, callback);
        return true;
    }

    private void handleOpenDevTools(CefBrowser browser, CefQueryCallback callback) {
        logger.info("正在打开 DevTools 窗口...");
        pendingCallbacks.add(callback);

        // 如果已有 DevTools，先关闭清理再重新打开
        closeDevTools();

        CefBrowser devTools = browser.getDevTools();
        devToolsBrowser = devTools;

        SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = new JDialog(parentFrame, "Hippo Code - DevTools", false);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setSize(960, 640);
                dialog.setLocationRelativeTo(parentFrame);
                dialog.add(devTools.getUIComponent(), BorderLayout.CENTER);

                // 窗口关闭时自动清理 CEF 浏览器资源
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        logger.info("DevTools 窗口已关闭，清理 CEF 资源");
                        closeDevTools();
                    }
                });

                dialog.setVisible(true);
                devToolsDialog = dialog;
                logger.info("DevTools 窗口已打开");
                callback.success("{}");
            } catch (Exception e) {
                logger.error("打开 DevTools 失败", e);
                callback.failure(500, e.getMessage());
                // 异常时清理，避免泄漏
                closeDevTools();
            } finally {
                pendingCallbacks.remove(callback);
            }
        });
    }

    /** 关闭并清理 DevTools 资源（可安全重复调用） */
    private void closeDevTools() {
        // 先置空引用，防止重入
        JDialog dialog = devToolsDialog;
        CefBrowser browser = devToolsBrowser;
        devToolsDialog = null;
        devToolsBrowser = null;

        if (dialog != null) {
            dialog.dispose();
        }
        if (browser != null) {
            // 使用 close(false) 避免阻塞 CEF 线程或 EDT，防止死锁
            browser.close(false);
        }
    }
}
