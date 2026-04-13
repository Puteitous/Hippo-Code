package com.example.agent.mcp.client;

import com.example.agent.mcp.config.McpConfig;
import com.example.agent.mcp.exception.McpConnectionException;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SseMcpClient extends AbstractMcpClient {

    private static final Logger logger = LoggerFactory.getLogger(SseMcpClient.class);

    private OkHttpClient okHttpClient;
    private EventSource eventSource;
    private String endpointUrl;
    private ExecutorService executor;
    private final AtomicInteger requestId = new AtomicInteger(1);

    public SseMcpClient(McpConfig.McpServerConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = config.getUrl();
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
                this.executor = Executors.newFixedThreadPool(2);

                Request sseRequest = new Request.Builder()
                        .url(endpointUrl + "sse")
                        .addHeader("Accept", "text/event-stream")
                        .build();

                EventSource.Factory factory = EventSources.createFactory(okHttpClient);
                this.eventSource = factory.newEventSource(sseRequest, new EventSourceListener() {
                    @Override
                    public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
                        logger.debug("收到SSE事件: type={}, data={}", type, data);
                        jsonRpcHandler.handleResponse(data);
                    }

                    @Override
                    public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
                        if (connected) {
                            logger.error("SSE连接错误", t);
                        }
                    }

                    @Override
                    public void onClosed(@NotNull EventSource eventSource) {
                        logger.info("SSE连接已关闭");
                        connected = false;
                    }
                });

                Thread.sleep(1000);
                connected = true;
                logger.info("SSE MCP服务器连接成功");
                return null;
            } catch (Exception e) {
                throw new McpConnectionException("连接SSE MCP服务器失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    protected CompletableFuture<String> sendRequestInternal(String method, Object params) {
        int id = requestId.getAndIncrement();
        CompletableFuture<JsonNode> future = jsonRpcHandler.registerPendingRequest(id);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestJson = jsonRpcHandler.createRequest(method, params);
                logger.debug("发送MCP请求: {}", requestJson);

                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl + "message"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .timeout(Duration.ofMillis(requestTimeoutMs))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 400) {
                    throw new IOException("HTTP error: " + response.statusCode());
                }

                return future.get();
            } catch (Exception e) {
                throw new RuntimeException("发送请求失败: " + method, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            connected = false;
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

            logger.info("SSE MCP连接已关闭: {}", getServerId());
        });
    }
}
