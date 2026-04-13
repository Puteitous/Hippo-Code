package com.example.agent.mcp.client;

import com.example.agent.mcp.model.McpResource;
import com.example.agent.mcp.model.McpTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface McpClient {

    String getServerId();

    String getServerName();

    boolean isConnected();

    CompletableFuture<Void> connect();

    CompletableFuture<Void> disconnect();

    CompletableFuture<Void> initialize();

    CompletableFuture<List<McpTool>> listTools();

    CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments);

    CompletableFuture<List<McpResource>> listResources();

    CompletableFuture<Object> readResource(String uri);
}
