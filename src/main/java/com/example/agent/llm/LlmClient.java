package com.example.agent.llm;

import com.example.agent.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class LlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";
    private static final int API_TIMEOUT_SECONDS = 60;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Config config;

    public LlmClient() {
        this(Config.getInstance());
    }

    public LlmClient(Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    public ChatResponse chat(List<Message> messages) throws LlmException {
        return chat(messages, null);
    }

    public ChatResponse chat(List<Message> messages, List<Tool> tools) throws LlmException {
        ChatRequest request = ChatRequest.of(config.getModel(), messages)
                .maxTokens(config.getMaxTokens());
        
        if (tools != null && !tools.isEmpty()) {
            request.tools(tools).toolChoiceAuto();
        }
        
        return executeRequest(request);
    }

    public ChatResponse chatWithTools(List<Message> messages, List<Tool> tools) throws LlmException {
        return chat(messages, tools);
    }

    public ChatResponse executeRequest(ChatRequest request) throws LlmException {
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
            throw new LlmException("API 请求超时（" + API_TIMEOUT_SECONDS + "秒）。请检查网络连接或稍后重试。", e);
        } catch (java.net.ConnectException e) {
            throw new LlmException("无法连接到 API 服务器: " + config.getBaseUrl() + "。请检查网络连接。", e);
        } catch (java.net.SocketTimeoutException e) {
            throw new LlmException("连接超时。请检查网络连接或稍后重试。", e);
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
                throw new LlmException("解析 API 响应失败: " + e.getMessage() + "\n响应内容: " + truncate(body, 500));
            }
        }
        
        String errorMessage = parseErrorMessage(body, statusCode);
        
        switch (statusCode) {
            case 400:
                throw new LlmException("请求参数错误: " + errorMessage);
            case 401:
                throw new LlmException("API Key 无效或已过期。请检查 config.json 中的 apiKey 配置。");
            case 403:
                throw new LlmException("访问被拒绝。请检查 API Key 权限或账户状态。");
            case 404:
                throw new LlmException("API 端点不存在。请检查 baseUrl 配置: " + config.getBaseUrl());
            case 429:
                throw new LlmException("请求过于频繁，已触发限流。请稍后重试。\n" + errorMessage);
            case 500:
            case 502:
            case 503:
                throw new LlmException("API 服务器错误 (" + statusCode + ")。请稍后重试。");
            default:
                throw new LlmException("API 请求失败 (HTTP " + statusCode + "): " + errorMessage);
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

    public ChatResponse continueWithToolResult(ChatResponse previousResponse, List<Message> messages, String toolCallId, String toolName, String toolResult) throws LlmException {
        Message assistantMessage = previousResponse.getFirstMessage();
        
        messages.add(assistantMessage);
        messages.add(Message.toolResult(toolCallId, toolName, toolResult));
        
        return chat(messages);
    }

    public ChatResponse continueWithToolResults(ChatResponse previousResponse, List<Message> messages, List<ToolResult> toolResults) throws LlmException {
        Message assistantMessage = previousResponse.getFirstMessage();
        messages.add(assistantMessage);
        
        for (ToolResult result : toolResults) {
            messages.add(Message.toolResult(result.getToolCallId(), result.getToolName(), result.getResult()));
        }
        
        return chat(messages);
    }

    public static class ToolResult {
        private final String toolCallId;
        private final String toolName;
        private final String result;

        public ToolResult(String toolCallId, String toolName, String result) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.result = result;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public String getToolName() {
            return toolName;
        }

        public String getResult() {
            return result;
        }
    }
}
