package com.example.agent.web.handler;

import com.example.agent.tools.FileChangeTracker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 获取文件 AI 修改前的原始内容。
 *
 * 策略：仅从 AI 变更记录（FileChangeTracker）中取原始内容。
 *   git 路线已注释（以前 git show HEAD 为准，但会显示全量 diff 而非仅 AI 改动）。
 *
 * 查询参数：?path=<绝对路径>
 * 返回 JSON：{"content":"...", "source":"ai"} 或 {"error":"..."}
 */
public class DiffOriginalHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(DiffOriginalHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

        String query = exchange.getRequestURI().getQuery();
        String filePath = null;

        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "path".equals(kv[0])) {
                    filePath = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        }

        if (filePath == null || filePath.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Missing path parameter\"}");
            return;
        }

        Path absPath = Path.of(filePath).normalize();
        if (!Files.exists(absPath)) {
            sendJson(exchange, 404, "{\"error\":\"File not found\"}");
            return;
        }

        // 1) 优先尝试 git（已注释，统一走 AI 路线）
//        String gitContent = tryGitShow(absPath);
//        if (gitContent != null) {
//            sendJson(exchange, 200, toJson(gitContent, "git"));
//            return;
//        }

        // 2) AI 变更记录
        String aiContent = tryAiTracker(absPath);
        if (aiContent != null) {
            sendJson(exchange, 200, toJson(aiContent, "ai"));
            return;
        }

        // 3) 无可用基线
        sendJson(exchange, 200, "{}");
    }

    /**
     * 执行 git show HEAD:<relativePath>（已注释，当前统一走 AI 路线）。
     */
    private static String tryGitShow(Path absPath) {
//        try {
//            // 查找 git 根目录
//            Path dir = absPath.getParent();
//            ProcessBuilder rootPb = new ProcessBuilder(
//                "git", "-C", dir.toString(), "rev-parse", "--show-toplevel"
//            );
//            rootPb.redirectErrorStream(true);
//            rootPb.environment().put("GIT_PAGER", "cat");
//
//            Process rootProc = rootPb.start();
//            if (!rootProc.waitFor(3, TimeUnit.SECONDS)) {
//                rootProc.destroyForcibly();
//                return null;
//            }
//            if (rootProc.exitValue() != 0) return null;
//
//            String gitRoot;
//            try (BufferedReader br = new BufferedReader(
//                    new InputStreamReader(rootProc.getInputStream(), StandardCharsets.UTF_8))) {
//                gitRoot = br.readLine();
//            }
//            if (gitRoot == null || gitRoot.isBlank()) return null;
//
//            // 计算相对路径
//            Path gitRootPath = Path.of(gitRoot).normalize();
//            String relativePath = gitRootPath.relativize(absPath).toString().replace('\\', '/');
//
//            // git show HEAD:<relativePath>
//            ProcessBuilder showPb = new ProcessBuilder(
//                "git", "-C", gitRoot, "show", "HEAD:" + relativePath
//            );
//            showPb.redirectErrorStream(true);
//            showPb.environment().put("GIT_PAGER", "cat");
//
//            Process showProc = showPb.start();
//            if (!showProc.waitFor(5, TimeUnit.SECONDS)) {
//                showProc.destroyForcibly();
//                return null;
//            }
//
//            // 读取全部输出
//            StringBuilder out = new StringBuilder();
//            try (BufferedReader br = new BufferedReader(
//                    new InputStreamReader(showProc.getInputStream(), StandardCharsets.UTF_8))) {
//                String line;
//                while ((line = br.readLine()) != null) {
//                    if (out.length() > 0) out.append('\n');
//                    out.append(line);
//                }
//            }
//
//            if (showProc.exitValue() != 0) return null;
//            // 空内容也是有效的原始内容（空文件）
//            return out.toString();
//        } catch (Exception e) {
//            logger.debug("git show 失败: {} - {}", absPath, e.getMessage());
//            return null;
//        }
        return null;
    }

    /**
     * 从 AI 变更记录中取最后一次变更的原始内容。
     * 新建文件也返回空字符串（diff 插件会标记所有行为新增）。
     * <p>
     * 如果变更是从磁盘加载的历史记录（恢复旧会话），
     * 则跳过 diff 展示，避免编辑器中出现不必要的行标记。
     */
    private static String tryAiTracker(Path absPath) {
        try {
            FileChangeTracker.FileChange change = FileChangeTracker.getLastChange(absPath.toString());
            if (change != null) {
                // 非 git 项目的历史变更（从磁盘加载）→ 跳过 preview diff
                if (FileChangeTracker.isHistoricalChange(change)) {
                    return null;
                }
                return change.originalContent != null ? change.originalContent : "";
            }
        } catch (Exception e) {
            logger.debug("AI 变更记录查询失败: {} - {}", absPath, e.getMessage());
        }
        return null;
    }

    private static String toJson(String content, String source) {
        try {
            Map<String, String> map = new HashMap<>();
            map.put("content", content);
            map.put("source", source);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"content\":\"\",\"source\":\"" + source + "\"}";
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
