package com.example.agent.web.handler;

import com.example.agent.tools.BashProcessManager;
import com.example.agent.web.session.SessionCancelManager;
import com.example.agent.web.util.SseWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ToolAbortHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ToolAbortHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SessionCancelManager cancelManager = SessionCancelManager.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(requestBody);

            String sessionId = json.has("sessionId") ? json.get("sessionId").asText() : null;
            String toolCallId = json.has("toolCallId") ? json.get("toolCallId").asText() : null;

            // 1. 设置会话级取消标志 — 让 Agent 循环和 LLM 流式读取线程尽快感知取消
            if (sessionId != null && !sessionId.isEmpty()) {
                cancelManager.cancel(sessionId);
                logger.info("已设置会话取消标志: sessionId={}", sessionId);
            }

            // 2. 如果有 toolCallId，同步杀死对应的 bash 进程
            if (toolCallId != null && !toolCallId.isEmpty()) {
                boolean killed = BashProcessManager.getInstance().cancel(toolCallId);
                if (killed) {
                    logger.info("已中止进程: toolCallId={}", toolCallId);
                    sendJson(exchange, 200, "{\"success\":true,\"message\":\"进程已终止\"}");
                } else {
                    logger.info("未找到运行中的进程: toolCallId={}", toolCallId);
                    sendJson(exchange, 200, "{\"success\":true,\"message\":\"未找到运行中的进程\"}");
                }
            } else {
                // 纯会话级取消请求（前端兜底请求），无需杀进程
                logger.info("纯会话取消请求，无 toolCallId: sessionId={}", sessionId);
                sendJson(exchange, 200, "{\"success\":true,\"message\":\"已设置取消标志\"}");
            }
        } catch (Exception e) {
            logger.error("处理中止请求失败", e);
            sendJson(exchange, 500, "{\"error\":\"" + SseWriter.escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
