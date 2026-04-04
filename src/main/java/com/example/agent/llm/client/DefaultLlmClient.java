package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.llm.model.ChatRequest;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Choice;
import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Tool;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.llm.model.Usage;
import com.example.agent.llm.retry.RetryPolicy;
import com.example.agent.llm.stream.SseParser;
import com.example.agent.llm.stream.StreamChunk;
import com.example.agent.llm.stream.ToolCallDelta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DefaultLlmClient implements LlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";
    private static final int API_TIMEOUT_SECONDS = 60;
    private static final int STREAM_TIMEOUT_SECONDS = 120;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Config config;
    private final RetryPolicy retryPolicy;
    private final SseParser sseParser;

    public DefaultLlmClient() {
        this(Config.getInstance());
    }

    public DefaultLlmClient(Config config) {
        this(config, RetryPolicy.defaultPolicy());
    }

    public DefaultLlmClient(Config config, RetryPolicy retryPolicy) {
        this.config = config;
        this.retryPolicy = retryPolicy;
        this.objectMapper = new ObjectMapper();
        this.sseParser = new SseParser();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public ChatResponse chat(List<Message> messages) throws LlmException {
        return chat(messages, null);
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools) throws LlmException {
        ChatRequest request = ChatRequest.of(config.getModel(), messages)
                .maxTokens(config.getMaxTokens());
        
        if (tools != null && !tools.isEmpty()) {
            request.tools(tools).toolChoiceAuto();
        }
        
        return executeRequest(request);
    }

    @Override
    public ChatResponse chatWithTools(List<Message> messages, List<Tool> tools) throws LlmException {
        return chat(messages, tools);
    }

    @Override
    public ChatResponse chatStream(List<Message> messages, Consumer<StreamChunk> onChunk) throws LlmException {
        return chatStream(messages, null, onChunk);
    }

    @Override
    public ChatResponse chatStream(List<Message> messages, List<Tool> tools, Consumer<StreamChunk> onChunk) throws LlmException {
        ChatRequest request = ChatRequest.of(config.getModel(), messages)
                .stream(true)
                .maxTokens(config.getMaxTokens());
        
        if (tools != null && !tools.isEmpty()) {
            request.tools(tools).toolChoiceAuto();
        }
        
        return executeStreamRequest(request, onChunk);
    }

    private ChatResponse executeStreamRequest(ChatRequest request, Consumer<StreamChunk> onChunk) throws LlmException {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            String url = config.getBaseUrl() + CHAT_COMPLETIONS_PATH;
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(STREAM_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest, 
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            
            return processStreamResponse(response, onChunk);
            
        } catch (LlmException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmTimeoutException(
                "流式请求超时（" + STREAM_TIMEOUT_SECONDS + "秒）。请检查网络连接或稍后重试。", 
                STREAM_TIMEOUT_SECONDS, e);
        } catch (java.net.ConnectException e) {
            throw new LlmConnectionException(
                "无法连接到 API 服务器: " + config.getBaseUrl() + "。请检查网络连接。", 
                config.getBaseUrl(), e);
        } catch (Exception e) {
            throw new LlmException("流式请求失败: " + e.getMessage(), e);
        }
    }

    private ChatResponse processStreamResponse(
            HttpResponse<InputStream> response, 
            Consumer<StreamChunk> onChunk) throws LlmException {
        
        int statusCode = response.statusCode();
        
        if (statusCode < 200 || statusCode >= 300) {
            try {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                String errorMessage = parseErrorMessage(body, statusCode);
                throw new LlmApiException("API 返回错误 (HTTP " + statusCode + "): " + errorMessage, statusCode, body);
            } catch (Exception e) {
                if (e instanceof LlmException) {
                    throw (LlmException) e;
                }
                throw new LlmApiException("API 返回错误 (HTTP " + statusCode + ")", statusCode, null);
            }
        }
        
        StringBuilder fullContent = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        String finishReason = null;
        Usage usage = null;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                StreamChunk chunk = sseParser.parse(line);
                
                if (chunk == null) {
                    if (sseParser.isDone(line)) {
                        break;
                    }
                    continue;
                }
                
                if (chunk.hasContent()) {
                    fullContent.append(chunk.getContent());
                    if (onChunk != null) {
                        onChunk.accept(chunk);
                    }
                }
                
                if (chunk.isToolCall() && chunk.hasToolCalls()) {
                    mergeToolCallDeltas(toolCalls, chunk.getToolCallDeltas());
                }
                
                if (chunk.getFinishReason() != null) {
                    finishReason = chunk.getFinishReason();
                }
                
                if (chunk.hasUsage()) {
                    usage = chunk.getUsage();
                }
            }
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof LlmException) {
                throw (LlmException) e;
            }
            throw new LlmException("读取流式响应失败: " + e.getMessage(), e);
        }
        
        return buildChatResponse(fullContent.toString(), toolCalls, finishReason, usage);
    }

    private void mergeToolCallDeltas(List<ToolCall> toolCalls, List<ToolCallDelta> deltas) {
        for (ToolCallDelta delta : deltas) {
            int index = delta.getIndex() != null ? delta.getIndex() : toolCalls.size();
            
            while (toolCalls.size() <= index) {
                toolCalls.add(new ToolCall());
            }
            
            ToolCall toolCall = toolCalls.get(index);
            
            if (delta.getId() != null) {
                toolCall.setId(delta.getId());
            }
            
            if (delta.getType() != null) {
                toolCall.setType(delta.getType());
            }
            
            if (delta.getFunction() != null) {
                ToolCallDelta.FunctionDelta funcDelta = delta.getFunction();
                
                if (toolCall.getFunction() == null) {
                    toolCall.setFunction(new FunctionCall());
                }
                
                FunctionCall func = toolCall.getFunction();
                
                if (funcDelta.getName() != null) {
                    func.setName(funcDelta.getName());
                }
                
                if (funcDelta.getArguments() != null) {
                    String currentArgs = func.getArguments() != null ? func.getArguments() : "";
                    func.setArguments(currentArgs + funcDelta.getArguments());
                }
            }
        }
    }

    private ChatResponse buildChatResponse(String content, List<ToolCall> toolCalls, String finishReason, Usage usage) {
        ChatResponse response = new ChatResponse();
        response.setId("stream-" + System.currentTimeMillis());
        response.setObject("chat.completion");
        response.setCreated(System.currentTimeMillis() / 1000);
        response.setModel(config.getModel());
        
        Message message = new Message();
        message.setRole("assistant");
        
        if (content != null && !content.isEmpty()) {
            message.setContent(content);
        }
        
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }
        
        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason(finishReason);
        
        response.setChoices(List.of(choice));
        
        if (usage != null) {
            response.setUsage(usage);
        }
        
        return response;
    }

    @Override
    public ChatResponse executeRequest(ChatRequest request) throws LlmException {
        LlmException lastException = null;
        int attempt = 0;
        
        while (attempt <= retryPolicy.getMaxRetries()) {
            try {
                return doExecuteRequest(request);
            } catch (LlmException e) {
                lastException = e;
                
                if (!retryPolicy.shouldRetry(e, attempt)) {
                    throw e;
                }
                
                if (attempt < retryPolicy.getMaxRetries()) {
                    long delayMs = retryPolicy.getDelayMs(attempt);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LlmException("请求被中断", ie);
                    }
                }
                
                attempt++;
            }
        }
        
        throw lastException;
    }

    private ChatResponse doExecuteRequest(ChatRequest request) throws LlmException {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            
            String url = config.getBaseUrl() + CHAT_COMPLETIONS_PATH;
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(API_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            return handleResponse(response);
            
        } catch (LlmException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmTimeoutException(
                "API 请求超时（" + API_TIMEOUT_SECONDS + "秒）。请检查网络连接或稍后重试。", 
                API_TIMEOUT_SECONDS, e);
        } catch (java.net.ConnectException e) {
            throw new LlmConnectionException(
                "无法连接到 API 服务器: " + config.getBaseUrl() + "。请检查网络连接。", 
                config.getBaseUrl(), e);
        } catch (java.net.SocketTimeoutException e) {
            throw new LlmTimeoutException(
                "连接超时。请检查网络连接或稍后重试。", 
                CONNECT_TIMEOUT_SECONDS, e);
        } catch (Exception e) {
            throw new LlmException("API 请求失败: " + e.getMessage(), e);
        }
    }

    private ChatResponse handleResponse(HttpResponse<String> response) throws LlmException {
        int statusCode = response.statusCode();
        String body = response.body();
        
        if (statusCode >= 200 && statusCode < 300) {
            try {
                return objectMapper.readValue(body, ChatResponse.class);
            } catch (Exception e) {
                throw new LlmApiException(
                    "解析 API 响应失败: " + e.getMessage() + "\n响应内容: " + truncate(body, 500), 
                    statusCode, body);
            }
        }
        
        String errorMessage = parseErrorMessage(body, statusCode);
        
        switch (statusCode) {
            case 400:
                throw new LlmApiException("请求参数错误: " + errorMessage, statusCode, body);
            case 401:
                throw new LlmApiException(
                    "API Key 无效或已过期。请检查 config.json 中的 apiKey 配置。", statusCode, body);
            case 403:
                throw new LlmApiException(
                    "访问被拒绝。请检查 API Key 权限或账户状态。", statusCode, body);
            case 404:
                throw new LlmApiException(
                    "API 端点不存在。请检查 baseUrl 配置: " + config.getBaseUrl(), statusCode, body);
            case 429:
                throw new LlmApiException(
                    "请求过于频繁，已触发限流。请稍后重试。\n" + errorMessage, statusCode, body);
            case 500:
            case 502:
            case 503:
                throw new LlmApiException(
                    "API 服务器错误 (" + statusCode + ")。请稍后重试。", statusCode, body);
            default:
                throw new LlmApiException(
                    "API 请求失败 (HTTP " + statusCode + "): " + errorMessage, statusCode, body);
        }
    }

    private String parseErrorMessage(String body, int statusCode) {
        if (body == null || body.isEmpty()) {
            return "无错误详情";
        }
        
        try {
            JsonNode root = objectMapper.readTree(body);
            
            if (root.has("error")) {
                JsonNode error = root.get("error");
                if (error.isObject()) {
                    if (error.has("message")) {
                        return error.get("message").asText();
                    }
                    return error.toString();
                }
                return error.asText();
            }
            
            if (root.has("message")) {
                return root.get("message").asText();
            }
            
            return truncate(body, 200);
        } catch (Exception e) {
            return truncate(body, 200);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    @Override
    public ChatResponse continueWithToolResult(ChatResponse previousResponse, List<Message> messages, String toolCallId, String toolName, String toolResult) throws LlmException {
        Message assistantMessage = previousResponse.getFirstMessage();
        
        messages.add(assistantMessage);
        messages.add(Message.toolResult(toolCallId, toolName, toolResult));
        
        return chat(messages);
    }

    @Override
    public ChatResponse continueWithToolResults(ChatResponse previousResponse, List<Message> messages, List<ToolResult> toolResults) throws LlmException {
        Message assistantMessage = previousResponse.getFirstMessage();
        messages.add(assistantMessage);
        
        for (ToolResult result : toolResults) {
            messages.add(Message.toolResult(result.getToolCallId(), result.getToolName(), result.getResult()));
        }
        
        return chat(messages);
    }
}