package com.example.agent.mcp.client;

import com.example.agent.mcp.config.McpConfig;
import com.example.agent.mcp.exception.McpConnectionException;
import com.example.agent.mcp.exception.McpException;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SseMcpClient extends AbstractMcpClient {

    private static final Logger logger = LoggerFactory.getLogger(SseMcpClient.class);

    private OkHttpClient okHttpClient;
    private HttpClient httpClient;
    private EventSource eventSource;
    private String endpointUrl;
    private ExecutorService executor;
    private final CompletableFuture<Void> connectionReady = new CompletableFuture<>();


    public SseMcpClient(McpConfig.McpServerConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = serverConfig.getUrl();
                if (baseUrl == null || baseUrl.isEmpty()) {
                    throw new McpConnectionException("SSE服务器URL未配置");
                }

                logger.info("连接SSE MCP服务器: {}", baseUrl);

                this.okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .readTimeout(Duration.ofSeconds(300))
                        .writeTimeout(Duration.ofSeconds(30))
                        .build();

                this.endpointUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                this.executor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "sse-mcp-" + getServerId());
                    t.setDaemon(true);
                    return t;
                });

                Request sseRequest = new Request.Builder()
                        .url(endpointUrl + "sse")
                        .addHeader("Accept", "text/event-stream")
                        .build();

                this.httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                EventSource.Factory factory = EventSources.createFactory(okHttpClient);
                this.eventSource = factory.newEventSource(sseRequest, new EventSourceListener() {
                    @Override
                    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                        connectionReady.complete(null);
                    }

                    @Override
                    public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
                        logger.debug("收到SSE事件: type={}, data={}", type, data);
                        jsonRpcHandler.handleResponse(data);
                    }

                    @Override
                    public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
                        connectionReady.completeExceptionally(new McpConnectionException("SSE连接失败", t));
                        if (connected) {
                            logger.error("SSE连接错误", t);
                            onConnectionLost();
                        }
                    }

                    @Override
                    public void onClosed(@NotNull EventSource eventSource) {
                        logger.info("SSE连接已关闭");
                        if (connected) {
                            onConnectionLost();
                        }
                    }
                });

                connectionReady.get(30, TimeUnit.SECONDS);
                connected = true;
                resetReconnectState();
                logger.info("SSE MCP服务器连接成功");
                return null;
            } catch (Exception e) {
                throw new McpConnectionException("连接SSE MCP服务器失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    protected void doSendMessage(String messageJson) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl + "message"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(messageJson))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() >= 400) {
                throw new IOException("HTTP error: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new McpException("发送消息失败", e);
        }
    }

    @Override
    protected CompletableFuture<JsonNode> sendRequestInternal(String method, Object params) {
        int id = jsonRpcHandler.nextId();
        CompletableFuture<JsonNode> future = jsonRpcHandler.registerPendingRequest(id);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestJson = jsonRpcHandler.createRequest(id, method, params);
                logger.debug("发送MCP请求: {} id={}", requestJson, id);
                doSendMessage(requestJson);
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException("发送请求失败: " + method, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            markUserInitiatedDisconnect();
            connected = false;
            connectionReady.completeExceptionally(new McpException("连接已断开"));
            jsonRpcHandler.cancelAllPending();

            if (executor != null) {
                executor.shutdownNow();
            }

            if (eventSource != null) {
                eventSource.cancel();
            }

            if (okHttpClient != null) {
                okHttpClient.dispatcher().cancelAll();
            }

            if (httpClient != null) {
                httpClient.close();
            }

            logger.info("SSE MCP连接已关闭: {}", getServerId());
        });
    }
}
