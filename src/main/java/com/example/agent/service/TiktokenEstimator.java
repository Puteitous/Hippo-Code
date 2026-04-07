package com.example.agent.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;

public class TiktokenEstimator implements TokenEstimator {

    private static final int DEFAULT_CACHE_MAX_SIZE = 1000;

    private final Encoding encoding;
    private final Map<String, Integer> cache;
    private final boolean cacheEnabled;

    public TiktokenEstimator() {
        this(ModelType.GPT_4, true, DEFAULT_CACHE_MAX_SIZE);
    }

    public TiktokenEstimator(ModelType modelType) {
        this(modelType, true, DEFAULT_CACHE_MAX_SIZE);
    }

    public TiktokenEstimator(String modelName) {
        this(modelName, true, DEFAULT_CACHE_MAX_SIZE);
    }

    public TiktokenEstimator(ModelType modelType, boolean cacheEnabled, int cacheMaxSize) {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncodingForModel(modelType);
        this.cacheEnabled = cacheEnabled;
        this.cache = createCache(cacheMaxSize);
    }

    public TiktokenEstimator(EncodingType encodingType, boolean cacheEnabled, int cacheMaxSize) {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(encodingType);
        this.cacheEnabled = cacheEnabled;
        this.cache = createCache(cacheMaxSize);
    }

    public TiktokenEstimator(String modelName, boolean cacheEnabled, int cacheMaxSize) {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        ModelType modelType = resolveModelType(modelName);
        this.encoding = registry.getEncodingForModel(modelType);
        this.cacheEnabled = cacheEnabled;
        this.cache = createCache(cacheMaxSize);
    }

    private Map<String, Integer> createCache(int maxSize) {
        return Collections.synchronizedMap(
            new LinkedHashMap<String, Integer>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > maxSize;
                }
            }
        );
    }

    private ModelType resolveModelType(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return ModelType.GPT_4;
        }
        
        String lowerName = modelName.toLowerCase();
        if (lowerName.contains("gpt-4")) {
            if (lowerName.contains("32k")) {
                return ModelType.GPT_4_32K;
            }
            return ModelType.GPT_4;
        }
        if (lowerName.contains("gpt-3.5") || lowerName.contains("gpt-35")) {
            if (lowerName.contains("16k")) {
                return ModelType.GPT_3_5_TURBO_16K;
            }
            return ModelType.GPT_3_5_TURBO;
        }
        
        return ModelType.GPT_4;
    }

    @Override
    public int estimateConversationTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        int total = 0;
        for (Message msg : messages) {
            if (msg != null) {
                total += estimateMessageTokens(msg);
            }
        }
        return total;
    }

    @Override
    public int estimateMessageTokens(Message msg) {
        if (msg == null) {
            return 0;
        }
        
        int tokens = 4;
        
        if (msg.getRole() != null) {
            tokens += estimateTextTokens(msg.getRole());
        }
        
        if (msg.getContent() != null) {
            tokens += estimateTextTokens(msg.getContent());
        }
        
        if (msg.getName() != null) {
            tokens += estimateTextTokens(msg.getName()) + 1;
        }
        
        if (msg.getToolCalls() != null) {
            for (ToolCall tc : msg.getToolCalls()) {
                if (tc != null) {
                    tokens += estimateToolCallTokens(tc);
                }
            }
        }
        
        if (msg.getToolCallId() != null) {
            tokens += estimateTextTokens(msg.getToolCallId()) + 1;
        }
        
        return tokens;
    }

    private int estimateToolCallTokens(ToolCall toolCall) {
        int tokens = 0;
        
        if (toolCall.getId() != null) {
            tokens += estimateTextTokens(toolCall.getId());
        }
        
        if (toolCall.getType() != null) {
            tokens += estimateTextTokens(toolCall.getType());
        }
        
        if (toolCall.getFunction() != null) {
            if (toolCall.getFunction().getName() != null) {
                tokens += estimateTextTokens(toolCall.getFunction().getName());
            }
            if (toolCall.getFunction().getArguments() != null) {
                tokens += estimateTextTokens(toolCall.getFunction().getArguments());
            }
        }
        
        return tokens;
    }

    @Override
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (cacheEnabled) {
            return cache.computeIfAbsent(text, this::doEstimate);
        }
        return doEstimate(text);
    }

    private int doEstimate(String text) {
        return encoding.encode(text).size();
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public int getCacheSize() {
        return cache.size();
    }

    public void clearCache() {
        cache.clear();
    }
}
