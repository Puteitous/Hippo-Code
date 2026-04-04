package com.example.agent.llm.stream;

import com.example.agent.llm.model.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

/* 
 *  Server-Sent Events（服务器推送事件），用于解析 LLM 服务器返回的流式输出
 * 解析 LLM 服务器返回的流式输出，将每个分块转换为 StreamChunk 对象
 */

public class SseParser {

    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";

    private final ObjectMapper objectMapper;

    public SseParser() {
        this.objectMapper = new ObjectMapper();
    }

    public StreamChunk parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        if (!line.startsWith(DATA_PREFIX)) {
            return null;
        }

        String data = line.substring(DATA_PREFIX.length()).trim();

        if (DONE_MARKER.equals(data)) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            return parseChunk(root);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isDone(String line) {
        if (line == null) {
            return false;
        }
        return line.startsWith(DATA_PREFIX) && 
               line.substring(DATA_PREFIX.length()).trim().equals(DONE_MARKER);
    }

    private StreamChunk parseChunk(JsonNode root) {
        StreamChunk chunk = new StreamChunk();
        
        Usage usage = parseUsage(root);
        if (usage != null) {
            chunk.setUsage(usage);
        }

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            return chunk;
        }

        JsonNode firstChoice = choices.get(0);
        
        String finishReason = getTextValue(firstChoice, "finish_reason");
        if (finishReason != null) {
            chunk.setFinishReason(finishReason);
        }

        JsonNode delta = firstChoice.get("delta");
        if (delta == null) {
            return chunk;
        }

        String content = getTextValue(delta, "content");
        if (content != null) {
            chunk.setContent(content);
        }

        JsonNode toolCalls = delta.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            List<ToolCallDelta> toolCallDeltas = parseToolCalls((ArrayNode) toolCalls);
            if (!toolCallDeltas.isEmpty()) {
                chunk.setToolCallDeltas(toolCallDeltas);
                chunk.setToolCall(true);
            }
        }

        return chunk;
    }
    
    private Usage parseUsage(JsonNode root) {
        JsonNode usageNode = root.get("usage");
        if (usageNode == null) {
            return null;
        }
        
        Usage usage = new Usage();
        
        if (usageNode.has("prompt_tokens")) {
            usage.setPromptTokens(usageNode.get("prompt_tokens").asInt());
        }
        
        if (usageNode.has("completion_tokens")) {
            usage.setCompletionTokens(usageNode.get("completion_tokens").asInt());
        }
        
        if (usageNode.has("total_tokens")) {
            usage.setTotalTokens(usageNode.get("total_tokens").asInt());
        }
        
        return usage;
    }

    private List<ToolCallDelta> parseToolCalls(ArrayNode toolCallsArray) {
        List<ToolCallDelta> deltas = new ArrayList<>();

        for (JsonNode node : toolCallsArray) {
            ToolCallDelta delta = new ToolCallDelta();

            if (node.has("index")) {
                delta.setIndex(node.get("index").asInt());
            }

            if (node.has("id")) {
                delta.setId(node.get("id").asText());
            }

            if (node.has("type")) {
                delta.setType(node.get("type").asText());
            }

            JsonNode function = node.get("function");
            if (function != null) {
                ToolCallDelta.FunctionDelta funcDelta = new ToolCallDelta.FunctionDelta();

                if (function.has("name")) {
                    funcDelta.setName(function.get("name").asText());
                }

                if (function.has("arguments")) {
                    funcDelta.setArguments(function.get("arguments").asText());
                }

                delta.setFunction(funcDelta);
            }

            deltas.add(delta);
        }

        return deltas;
    }

    private String getTextValue(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        JsonNode fieldNode = node.get(field);
        return fieldNode.isNull() ? null : fieldNode.asText();
    }
}
