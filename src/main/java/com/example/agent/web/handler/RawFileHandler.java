package com.example.agent.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 提供原始二进制文件下载（用于图片、PDF、Office 文件的预览）。
 * <p>
 * 路径通过 query parameter {@code path} 传入（绝对路径），
 * 返回文件原始字节流 + 对应 Content-Type。
 * 限制：超过 50MB 的文件返回 413 错误，避免 OOM。
 * </p>
 */
public class RawFileHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RawFileHandler.class);

    /** 单文件预览大小上限：50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    /** 人类可读的大小上限 */
    private static final String MAX_FILE_SIZE_STR = "50MB";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            sendError(exchange, 400, "缺少 'path' 查询参数");
            return;
        }

        String filePath = null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "path".equals(kv[0])) {
                filePath = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                break;
            }
        }

        if (filePath == null || filePath.isBlank()) {
            sendError(exchange, 400, "缺少 'path' 查询参数");
            return;
        }

        Path file = Paths.get(filePath).normalize();
        if (!Files.isRegularFile(file)) {
            sendError(exchange, 404, "文件未找到: " + filePath);
            return;
        }

        long fileSize = Files.size(file);

        // ── 超大文件检查 ──
        if (fileSize > MAX_FILE_SIZE) {
            String sizeStr = formatFileSize(fileSize);
            sendError(exchange, 413,
                "文件过大（" + sizeStr + "），超过预览大小上限 " + MAX_FILE_SIZE_STR
                + "，请在本地打开");
            return;
        }

        String mimeType = getMimeType(file.getFileName().toString());

        byte[] content = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(content.length));
        exchange.getResponseHeaders().set("Content-Size-Human", formatFileSize(fileSize));
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.getResponseHeaders().set("Cache-Control", "private, max-age=3600");

        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    /** 格式化文件大小为人类可读形式 */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getMimeType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
