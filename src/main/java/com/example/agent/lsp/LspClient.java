package com.example.agent.lsp;

import com.example.agent.lsp.model.Hover;
import com.example.agent.lsp.model.Location;
import com.example.agent.lsp.model.SymbolInformation;
import com.example.agent.mcp.protocol.JsonRpcHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LspClient {

    private static final Logger logger = LoggerFactory.getLogger(LspClient.class);
    private static final Pattern CONTENT_LENGTH_PATTERN = Pattern.compile("Content-Length: (\\d+)");
    private static final TypeReference<List<Location>> LOCATION_LIST_TYPE = new TypeReference<List<Location>>() {};
    private static final TypeReference<List<SymbolInformation>> SYMBOL_LIST_TYPE = new TypeReference<List<SymbolInformation>>() {};



    private final String languageId;
    private final String command;
    private final List<String> args;
    private final Path workspaceRoot;
    private final Map<String, String> env;

    private final JsonRpcHandler jsonRpcHandler = new JsonRpcHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Boolean> openedDocuments = new ConcurrentHashMap<>();

    private Process process;
    private BufferedReader stdoutReader;
    private BufferedWriter stdinWriter;
    private BufferedReader stderrReader;

    private volatile boolean connected = false;
    private volatile boolean initialized = false;
    private volatile long initializedTimestamp = 0;
    private volatile boolean shuttingDown = false;

    public boolean isIndexingInProgress() {
        return initialized && (System.currentTimeMillis() - initializedTimestamp) < 120000;
    }

    public long getInitializedTimestamp() {
        return initializedTimestamp;
    }

    public LspClient(String languageId, String command, List<String> args, Path workspaceRoot) {
        this(languageId, command, args, workspaceRoot, Collections.emptyMap());
    }

    public LspClient(String languageId, String command, List<String> args, Path workspaceRoot, Map<String, String> env) {
        this.languageId = languageId;
        this.command = resolveVariables(command);
        this.args = args != null ? args.stream().map(LspClient::resolveVariables).collect(Collectors.toList()) : new ArrayList<>();
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.env = env != null ? env.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> resolveVariables(e.getValue()))) : new HashMap<>();
    }

    private static String resolveVariables(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        result = result.replace("${user_home}", System.getProperty("user.home"));
        result = result.replace("${user.dir}", System.getProperty("user.dir"));
        return result;
    }

    private List<String> buildCommandList() {
        List<String> fullCommand = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        // 处理 Windows 下 bat/cmd 脚本启动问题
        if (os.contains("win") && (command.endsWith(".bat") || command.endsWith(".cmd"))) {
            fullCommand.add("cmd.exe");
            fullCommand.add("/c");
        }
        fullCommand.add(command);
        fullCommand.addAll(args);
        return fullCommand;
    }

    public CompletableFuture<Void> start() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("启动 LSP 服务器: {} {}", command, args);

            Process tempProcess = null;
            BufferedReader tempStdout = null;
            BufferedWriter tempStdin = null;
            BufferedReader tempStderr = null;
            ExecutorService tempExecutor = null;

            try {
                List<String> commandList = buildCommandList();
                logger.info("最终命令行: {}", commandList);

                ProcessBuilder pb = new ProcessBuilder(commandList);
                
                File workDir = workspaceRoot.toFile();
                if (workDir.exists() && workDir.isDirectory()) {
                    pb.directory(workDir);
                    logger.debug("工作目录: {}", workDir.getAbsolutePath());
                } else {
                    logger.warn("工作目录不存在，使用当前目录: {}", workDir.getAbsolutePath());
                }
                
                if (!env.isEmpty()) {
                    pb.environment().putAll(env);
                }

                tempProcess = pb.start();

                tempStdout = new BufferedReader(
                        new InputStreamReader(tempProcess.getInputStream(), StandardCharsets.UTF_8));
                tempStdin = new BufferedWriter(
                        new OutputStreamWriter(tempProcess.getOutputStream(), StandardCharsets.UTF_8));
                tempStderr = new BufferedReader(
                        new InputStreamReader(tempProcess.getErrorStream(), StandardCharsets.UTF_8));

                process = tempProcess;
                stdoutReader = tempStdout;
                stdinWriter = tempStdin;
                stderrReader = tempStderr;

                Thread stdoutThread = new Thread(this::readStdoutLoop, "lsp-" + languageId + "-stdout");
                Thread stderrThread = new Thread(this::readStderrLoop, "lsp-" + languageId + "-stderr");
                Thread monitorThread = new Thread(this::monitorProcessExit, "lsp-" + languageId + "-monitor");
                
                stdoutThread.setDaemon(true);
                stderrThread.setDaemon(true);
                monitorThread.setDaemon(true);
                
                stdoutThread.setUncaughtExceptionHandler((t, e) -> 
                    logger.error("线程 {} 异常退出: {}", t.getName(), e.getMessage(), e));
                stderrThread.setUncaughtExceptionHandler((t, e) -> 
                    logger.error("线程 {} 异常退出: {}", t.getName(), e.getMessage(), e));
                monitorThread.setUncaughtExceptionHandler((t, e) -> 
                    logger.error("线程 {} 异常退出: {}", t.getName(), e.getMessage(), e));

                stdoutThread.start();
                stderrThread.start();
                monitorThread.start();
                
                logger.info("所有 LSP IO 线程已启动");

                connected = true;
                logger.info("LSP 服务器进程启动成功");
                return null;
            } catch (Exception e) {
                cleanupResources(tempProcess, tempStdout, tempStdin, tempStderr);
                logger.error("LSP 服务器启动失败", e);
                throw new RuntimeException("LSP 服务器启动失败: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Void> initialize() {
        if (!connected) {
            throw new IllegalStateException("LSP 未连接，请先调用 start()");
        }

        Map<String, Object> textDocumentCaps = new HashMap<>();
        textDocumentCaps.put("definition", Map.of("linkSupport", true));
        textDocumentCaps.put("references", Map.of());
        textDocumentCaps.put("hover", Map.of("contentFormat", List.of("markdown", "plaintext")));
        textDocumentCaps.put("documentSymbol", Map.of(
                "hierarchicalDocumentSymbolSupport", false,
                "symbolKind", Map.of("valueSet", (Object) List.of())
        ));

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("textDocument", textDocumentCaps);
        capabilities.put("workspace", Map.of("symbol", Map.of()));

        Map<String, Object> params = new HashMap<>();
        params.put("processId", ProcessHandle.current().pid());
        params.put("rootUri", workspaceRoot.toUri().toString());
        params.put("rootPath", workspaceRoot.toString());
        params.put("capabilities", capabilities);

        logger.info("发送 LSP initialize 请求");

        logger.info("发送 initialize 请求，等待响应... (jdtls 首次启动需要 60-120 秒)");
        
        return sendRequest("initialize", params)
                .orTimeout(120, TimeUnit.SECONDS)
                .thenCompose(result -> {
                    logger.info("✅ LSP initialize 成功！服务器信息: {}", result.path("serverInfo"));
                    initialized = true;
                    initializedTimestamp = System.currentTimeMillis();
                    logger.info("LSP 初始化完成，开始建立索引（预计需要 60-120 秒）");
                    return sendNotification("initialized", Map.of());
                })
                .exceptionally(e -> {
                    logger.error("❌ LSP 初始化失败: {}", e.getMessage());
                    logger.info("  提示: jdtls 首次启动需要下载依赖，请检查网络连接");
                    logger.info("  提示: 检查任务管理器中 java.exe 的 CPU 使用率，高则表示正常建索引");
                    throw new RuntimeException("LSP 初始化失败: " + e.getMessage(), e);
                });
    }

    private CompletableFuture<Void> didOpen(String filePath) {
        if (!initialized) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("LSP 未初始化或正在启动中"));
            return future;
        }

        Path absolutePath = workspaceRoot.resolve(filePath).normalize();
        String uri = absolutePath.toUri().toString();

        if (openedDocuments.containsKey(uri)) {
            return CompletableFuture.completedFuture(null);
        }

        if (!Files.exists(absolutePath)) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("文件不存在: " + filePath));
            return future;
        }

        try {
            String content = Files.readString(absolutePath, StandardCharsets.UTF_8);

            Map<String, Object> textDocument = Map.of(
                    "uri", uri,
                    "languageId", languageId,
                    "version", 1,
                    "text", content
            );

            openedDocuments.put(uri, true);
            logger.debug("打开文档: {}", filePath);
            return sendNotification("textDocument/didOpen", Map.of("textDocument", textDocument));
        } catch (IOException e) {
            logger.warn("读取文件失败: {}", filePath, e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    public CompletableFuture<List<Location>> definition(String filePath, int line, int column) {
        if (filePath == null || filePath.trim().isEmpty()) {
            CompletableFuture<List<Location>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("文件路径不能为空"));
            return future;
        }
        if (line < 0) {
            CompletableFuture<List<Location>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("行号不能为负数: " + line));
            return future;
        }
        if (column < 0) {
            CompletableFuture<List<Location>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("列号不能为负数: " + column));
            return future;
        }
        return didOpen(filePath)
                .thenCompose(v -> {
                    Path absolutePath = workspaceRoot.resolve(filePath).normalize();
                    Map<String, Object> params = buildPositionParams(absolutePath, line, column);
                    return sendRequest("textDocument/definition", params);
                })
                .thenApply(result -> {
                    logger.info("【LSP definition】原始响应 JSON: {}", result);
                    
                    if (result == null || result.isNull()) {
                        return Collections.emptyList();
                    }
                    if (result.isArray()) {
                        return objectMapper.convertValue(result, LOCATION_LIST_TYPE);
                    }
                    return Collections.singletonList(objectMapper.convertValue(result, Location.class));
                });
    }

    public CompletableFuture<List<Location>> references(String filePath, int line, int column) {
        if (filePath == null || filePath.trim().isEmpty()) {
            CompletableFuture<List<Location>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("文件路径不能为空"));
            return future;
        }
        if (line < 0) {
            CompletableFuture<List<Location>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("行号不能为负数: " + line));
            return future;
        }
        if (column < 0) {
            CompletableFuture<List<Location>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("列号不能为负数: " + column));
            return future;
        }
        return didOpen(filePath)
                .thenCompose(v -> {
                    Path absolutePath = workspaceRoot.resolve(filePath).normalize();
                    Map<String, Object> params = new HashMap<>(buildPositionParams(absolutePath, line, column));
                    params.put("context", Map.of("includeDeclaration", true));
                    return sendRequest("textDocument/references", params);
                })
                .thenApply(result -> {
                    if (result == null || result.isNull()) {
                        return Collections.emptyList();
                    }
                    logger.info("【LSP references】原始响应 JSON: {}", result);
                    return objectMapper.convertValue(result, LOCATION_LIST_TYPE);
                });
    }

    public CompletableFuture<Hover> hover(String filePath, int line, int column) {
        if (filePath == null || filePath.trim().isEmpty()) {
            CompletableFuture<Hover> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("文件路径不能为空"));
            return future;
        }
        if (line < 0) {
            CompletableFuture<Hover> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("行号不能为负数: " + line));
            return future;
        }
        if (column < 0) {
            CompletableFuture<Hover> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("列号不能为负数: " + column));
            return future;
        }
        return didOpen(filePath)
                .thenCompose(v -> {
                    Path absolutePath = workspaceRoot.resolve(filePath).normalize();
                    Map<String, Object> params = buildPositionParams(absolutePath, line, column);
                    return sendRequest("textDocument/hover", params);
                })
                .thenApply(result -> {
                    if (result == null || result.isNull()) {
                        return new Hover();
                    }
                    return objectMapper.convertValue(result, Hover.class);
                });
    }

    public CompletableFuture<List<SymbolInformation>> documentSymbol(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            CompletableFuture<List<SymbolInformation>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("文件路径不能为空"));
            return future;
        }
        return didOpen(filePath)
                .thenCompose(v -> {
                    Path absolutePath = workspaceRoot.resolve(filePath).normalize();
                    Map<String, Object> params = Map.of(
                            "textDocument", Map.of("uri", absolutePath.toUri().toString())
                    );
                    return sendRequest("textDocument/documentSymbol", params);
                })
                .thenApply(result -> {
                    if (result == null || result.isNull()) {
                        return Collections.emptyList();
                    }
                    return objectMapper.convertValue(result, SYMBOL_LIST_TYPE);
                });
    }

    public CompletableFuture<List<SymbolInformation>> workspaceSymbol(String query) {
        CompletableFuture<List<SymbolInformation>> future = new CompletableFuture<>();
        try {
            if (!initialized) {
                throw new IllegalStateException("LSP 未初始化或正在启动中");
            }
            Map<String, Object> params;
            if (query != null) {
                params = Map.of("query", query);
            } else {
                params = Map.of("query", "");
            }
            return sendRequest("workspace/symbol", params)
                .thenApply(result -> {
                    if (result == null || result.isNull()) {
                        return Collections.emptyList();
                    }
                    return objectMapper.convertValue(result, SYMBOL_LIST_TYPE);
                });
        } catch (Exception e) {
            CompletableFuture<List<SymbolInformation>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private Map<String, Object> buildPositionParams(Path filePath, int line, int column) {
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", filePath.toUri().toString()));
        params.put("position", Map.of("line", line, "character", column));
        return params;
    }

    private CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        CompletableFuture<JsonNode> future;
        try {
            int id = jsonRpcHandler.nextId();
            future = jsonRpcHandler.registerPendingRequest(id);

            String json = jsonRpcHandler.createRequest(id, method, params);
            doSendMessage(json);

            return future.orTimeout(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            CompletableFuture<JsonNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private CompletableFuture<Void> sendNotification(String method, Object params) {
        String json = jsonRpcHandler.createNotification(method, params);
        doSendMessage(json);
        return CompletableFuture.completedFuture(null);
    }

    private void doSendMessage(String messageJson) {
        if (stdinWriter == null) {
            throw new IllegalStateException("LSP 连接未建立，请检查服务器是否启动成功");
        }
        try {
            String message = "Content-Length: " + messageJson.getBytes(StandardCharsets.UTF_8).length +
                    "\r\n\r\n" +
                    messageJson;

            synchronized (stdinWriter) {
                stdinWriter.write(message);
                stdinWriter.flush();
            }

            logger.debug("发送 LSP 消息: {}", messageJson);
        } catch (Exception e) {
            throw new RuntimeException("发送 LSP 消息失败: " + e.getMessage(), e);
        }
    }

    private void readStdoutLoop() {
        logger.info("LSP 消息读取线程已启动");
        try {
            InputStream in = process.getInputStream();
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
            int contentLength = -1;
            boolean inHeaders = true;

            while (!Thread.currentThread().isInterrupted() && connected) {
                int n = in.read(buffer);
                if (n == -1) {
                    logger.warn("LSP 输入流已关闭");
                    break;
                }

                messageBuffer.write(buffer, 0, n);

                while (true) {
                    byte[] data = messageBuffer.toByteArray();
                    
                    if (inHeaders) {
                        String dataStr = new String(data, StandardCharsets.ISO_8859_1);
                        int headerEnd = dataStr.indexOf("\r\n\r\n");
                        
                        if (headerEnd >= 0) {
                            String headers = dataStr.substring(0, headerEnd);
                            logger.debug("收到 LSP 头部: {}", headers);
                            
                            Matcher matcher = CONTENT_LENGTH_PATTERN.matcher(headers);
                            if (matcher.find()) {
                                contentLength = Integer.parseInt(matcher.group(1));
                                logger.debug("消息长度: {} 字节", contentLength);
                            }
                            
                            int bodyStart = headerEnd + 4;
                            int remaining = data.length - bodyStart;
                            messageBuffer.reset();
                            if (remaining > 0) {
                                messageBuffer.write(data, bodyStart, remaining);
                            }
                            inHeaders = false;
                            continue;
                        }
                    }
                    
                    if (!inHeaders && contentLength > 0 && messageBuffer.size() >= contentLength) {
                        byte[] body = new byte[contentLength];
                        messageBuffer.reset();
                        System.arraycopy(data, 0, body, 0, contentLength);
                        
                        int remaining = data.length - contentLength;
                        if (remaining > 0) {
                            messageBuffer.write(data, contentLength, remaining);
                        }
                        
                        String json = new String(body, StandardCharsets.UTF_8);
                        logger.debug("收到 LSP 消息: {}", json);
                        handleMessage(json);
                        
                        inHeaders = true;
                        contentLength = -1;
                        continue;
                    }
                    
                    break;
                }
            }
        } catch (Exception e) {
            if (!shuttingDown) {
                logger.error("LSP stdout 读取线程异常退出", e);
            }
        }
        logger.info("LSP 消息读取线程已退出");
    }

    private void handleMessage(String json) {
        try {
            jsonRpcHandler.handleResponse(json);
        } catch (Exception e) {
            logger.error("处理 LSP 消息失败: {}", json, e);
        }
    }

    private void readStderrLoop() {
        logger.info("LSP stderr 线程已启动");
        try {
            String line;
            while (!Thread.currentThread().isInterrupted() && connected) {
                line = stderrReader.readLine();
                if (line == null) break;
                if (!line.isEmpty()) {
                    logger.info("[LSP] {}", line);
                }
            }
        } catch (Exception e) {
            if (!shuttingDown) {
                logger.error("LSP stderr 读取线程异常退出", e);
            }
        }
        logger.info("LSP stderr 线程已退出");
    }

    private void monitorProcessExit() {
        try {
            int exitCode = process.waitFor();
            if (connected && !shuttingDown) {
                logger.warn("LSP 进程意外退出，退出码: {}", exitCode);
                connected = false;
                initialized = false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        shuttingDown = true;

        if (initialized) {
            try {
                sendRequest("shutdown", null).get(5, TimeUnit.SECONDS);
                sendNotification("exit", null);
            } catch (Exception e) {
                logger.debug("LSP 优雅关闭失败，强制终止");
            }
        }

        connected = false;
        initialized = false;

        cleanupResources(process, stdoutReader, stdinWriter, stderrReader);
        logger.info("LSP 客户端已关闭");
    }

    private void cleanupResources(Process p, BufferedReader out, BufferedWriter in, BufferedReader err) {
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}
        try {
            if (err != null) err.close();
        } catch (Exception ignored) {}
        if (p != null) {
            p.destroyForcibly();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public String getLanguageId() {
        return languageId;
    }
}
