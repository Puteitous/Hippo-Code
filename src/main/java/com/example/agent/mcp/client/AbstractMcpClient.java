package com.example.agent.mcp.client;

import com.example.agent.mcp.config.McpConfig;
import com.example.agent.mcp.exception.McpException;
import com.example.agent.mcp.exception.McpTimeoutException;
import com.example.agent.mcp.model.InitializeResult;
import com.example.agent.mcp.model.ListToolsResult;
import com.example.agent.mcp.model.McpResource;
import com.example.agent.mcp.model.McpTool;
import com.example.agent.mcp.protocol.JsonRpcHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractMcpClient implements McpClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractMcpClient.class);

    protected final McpConfig.McpServerConfig config;
    protected final JsonRpcHandler jsonRpcHandler;
    protected final int requestTimeoutMs;

    protected volatile boolean connected = false;
    protected String serverName;
    protected InitializeResult.ServerInfo serverInfo;

    protected AbstractMcpClient(McpConfig.McpServerConfig config) {
        this.config = config;
        this.jsonRpcHandler = new JsonRpcHandler();
        this.requestTimeoutMs = 60000;
        this.serverName = config.getName() != null ? config.getName() : config.getId();
    }

    @Override
    public String getServerId() {
        return config.getId();
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    protected abstract CompletableFuture<JsonNode> sendRequestInternal(String method, Object params);

    protected <T> CompletableFuture<T> sendRequest(String method, Object params, Class<T> resultType) {
        return sendRequestInternal(method, params)
                .orTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof java.util.concurrent.TimeoutException) {
                        throw new McpTimeoutException("请求超时: " + method);
                    }
                    throw new McpException("请求失败: " + method, ex);
                })
                .thenApply(result -> jsonRpcHandler.parseResult(result, resultType));
    }

    @Override
    public CompletableFuture<Void> initialize() {
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("name", "hippo-agent");
        clientInfo.put("version", "1.0.0");
        params.put("clientInfo", clientInfo);
        Map<String, Object> capabilities = new HashMap<>();
        params.put("capabilities", capabilities);

        return sendRequest("initialize", params, InitializeResult.class)
                .thenAccept(result -> {
                    this.serverInfo = result.getServerInfo();
                    if (serverInfo != null && serverInfo.getName() != null) {
                        this.serverName = serverInfo.getName();
                    }
                    logger.info("MCP服务器初始化成功: {} v{}",
                            result.getServerInfo().getName(),
                            result.getServerInfo().getVersion());
                })
                .thenCompose(v -> sendNotification("initialized", null));
    }

    protected abstract void doSendMessage(String messageJson);

    protected CompletableFuture<Void> sendNotification(String method, Object params) {
        return CompletableFuture.runAsync(() -> {
            try {
                String requestJson = jsonRpcHandler.createNotification(method, params);
                logger.debug("发送通知: {}", requestJson);
                doSendMessage(requestJson);
            } catch (Exception e) {
                logger.warn("发送通知失败", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<McpTool>> listTools() {
        return sendRequest("tools/list", null, ListToolsResult.class)
                .thenApply(ListToolsResult::getTools);
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);
        return sendRequest("tools/call", params, Object.class);
    }

    @Override
    public CompletableFuture<List<McpResource>> listResources() {
        throw new UnsupportedOperationException("Resources not implemented yet");
    }

    @Override
    public CompletableFuture<Object> readResource(String uri) {
        throw new UnsupportedOperationException("Resources not implemented yet");
    }
}
