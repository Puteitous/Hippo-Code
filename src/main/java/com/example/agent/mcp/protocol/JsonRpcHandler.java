package com.example.agent.mcp.protocol;

import com.example.agent.mcp.exception.McpProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonRpcHandler {

    private static final Logger logger = LoggerFactory.getLogger(JsonRpcHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    public String createRequest(String method, Object params) {
        int id = requestIdCounter.getAndIncrement();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", objectMapper.valueToTree(params));
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new McpProtocolException(-32603, "创建JSON-RPC请求失败", e);
        }
    }

    public CompletableFuture<JsonNode> registerPendingRequest(int id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        return future;
    }

    public void handleResponse(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);

            if (!json.has("jsonrpc")) {
                return;
            }

            if (json.has("id") && json.get("id").isNumber()) {
                int id = json.get("id").asInt();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);

                if (future != null) {
                    if (json.has("error")) {
                        JsonNode error = json.get("error");
                        int code = error.has("code") ? error.get("code").asInt() : -32603;
                        String errorMessage = error.has("message") ? error.get("message").asText() : "未知错误";
                        future.completeExceptionally(new McpProtocolException(code, errorMessage));
                    } else {
                        future.complete(json.get("result"));
                    }
                }
            } else if (json.has("method")) {
                handleNotification(json);
            }
        } catch (IOException e) {
            logger.debug("解析JSON-RPC消息失败: {}", message, e);
        }
    }

    private void handleNotification(JsonNode json) {
        String method = json.get("method").asText();
        JsonNode params = json.get("params");
        logger.debug("收到通知: {}, 参数: {}", method, params);
    }

    public <T> T parseResult(JsonNode result, Class<T> clazz) {
        try {
            return objectMapper.treeToValue(result, clazz);
        } catch (Exception e) {
            throw new McpProtocolException(-32603, "解析响应结果失败", e);
        }
    }

    public void cancelAllPending() {
        pendingRequests.forEach((id, future) -> {
            future.completeExceptionally(new McpProtocolException("请求已取消"));
        });
        pendingRequests.clear();
    }
}
