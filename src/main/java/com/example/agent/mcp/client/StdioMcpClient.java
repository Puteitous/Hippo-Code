package com.example.agent.mcp.client;

import com.example.agent.mcp.config.McpConfig;
import com.example.agent.mcp.exception.McpConnectionException;
import com.example.agent.mcp.exception.McpException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StdioMcpClient extends AbstractMcpClient {

    private static final Logger logger = LoggerFactory.getLogger(StdioMcpClient.class);

    private Process process;
    private BufferedReader stdoutReader;
    private BufferedWriter stdinWriter;
    private BufferedReader stderrReader;
    private ExecutorService executor;


    public StdioMcpClient(McpConfig.McpServerConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("启动MCP子进程: {} {}", serverConfig.getCommand(), serverConfig.getArgs());

                List<String> command = new ArrayList<>();
                command.add(serverConfig.getCommand());
                command.addAll(serverConfig.getArgs());

                ProcessBuilder pb = new ProcessBuilder(command);
                if (!serverConfig.getEnv().isEmpty()) {
                    pb.environment().putAll(serverConfig.getEnv());
                }

                process = pb.start();

                stdoutReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                stdinWriter = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

                executor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "stdio-mcp-" + getServerId());
                    t.setDaemon(true);
                    return t;
                });
                executor.submit(this::readStdoutLoop);
                executor.submit(this::readStderrLoop);
                executor.submit(this::monitorProcessExit);

                connected = true;
                resetReconnectState();
                logger.info("MCP子进程启动成功");
                return null;
            } catch (Exception e) {
                throw new McpConnectionException("启动MCP子进程失败: " + e.getMessage(), e);
            }
        });
    }

    private void monitorProcessExit() {
        try {
            int exitCode = process.waitFor();
            if (connected) {
                logger.warn("MCP子进程异常退出，退出码: {}", exitCode);
                onConnectionLost();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("进程监控线程被中断");
        }
    }

    @Override
    protected void doSendMessage(String messageJson) {
        try {
            synchronized (stdinWriter) {
                stdinWriter.write(messageJson);
                stdinWriter.newLine();
                stdinWriter.flush();
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
                throw new McpException("发送请求失败: " + method, e);
            }
        }, executor);
    }

    private void readStdoutLoop() {
        try {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    logger.debug("收到MCP消息: {}", line);
                    jsonRpcHandler.handleResponse(line);
                }
            }
        } catch (Exception e) {
            if (connected) {
                logger.error("读取stdout失败", e);
            }
        }
    }

    private void readStderrLoop() {
        try {
            String line;
            while ((line = stderrReader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    logger.warn("MCP stderr: {}", line);
                }
            }
        } catch (Exception e) {
            if (connected) {
                logger.error("读取stderr失败", e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            markUserInitiatedDisconnect();
            connected = false;

            jsonRpcHandler.cancelAllPending();

            if (executor != null) {
                executor.shutdownNow();
            }

            try {
                if (stdinWriter != null) {
                    stdinWriter.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (stdoutReader != null) {
                    stdoutReader.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (stderrReader != null) {
                    stderrReader.close();
                }
            } catch (Exception ignored) {
            }

            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }

            logger.info("MCP连接已关闭: {}", getServerId());
        });
    }
}
