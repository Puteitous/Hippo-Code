package com.example.agent.memory.embedding;

import com.example.agent.memory.MemoryMetricsCollector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API 远程向量化实现
 * 
 * 使用 text-embedding-3-small 模型
 * 维度：1536（默认）或可配置
 */
public class OpenAiEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAiEmbeddingService.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_DIMENSION = 1536;
    private static final int TIMEOUT_SECONDS = 30;
    private static final long PERFORMANCE_THRESHOLD_MS = 1000;
    private static final int MAX_CONSECUTIVE_SLOW = 3;
    
    private boolean available = false;
    private int consecutiveSlowCalls = 0;
    private MemoryMetricsCollector metricsCollector;
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimension;
    
    public OpenAiEmbeddingService(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL, DEFAULT_DIMENSION, null);
    }
    
    public OpenAiEmbeddingService(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, DEFAULT_MODEL, DEFAULT_DIMENSION, null);
    }
    
    public OpenAiEmbeddingService(String apiKey, String baseUrl, String model, int dimension) {
        this(apiKey, baseUrl, model, dimension, null);
    }
    
    public OpenAiEmbeddingService(String apiKey, String baseUrl, String model, int dimension, 
                                  MemoryMetricsCollector metricsCollector) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimension = dimension;
        this.metricsCollector = metricsCollector;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        initialize();
    }
    
    private void initialize() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("OpenAI API Key 未配置，远程向量化不可用");
            logger.warn("将使用关键词匹配作为降级方案");
            available = false;
            return;
        }
        
        try {
            // 测试 API 连接
            float[] testEmbedding = embed("test");
            if (testEmbedding.length > 0) {
                logger.info("✅ OpenAI 远程向量化服务初始化完成");
                logger.info("   模型：{}", model);
                logger.info("   向量维度：{}", dimension);
                logger.info("   API 端点：{}", baseUrl);
                available = true;
            } else {
                logger.warn("OpenAI 向量化测试失败");
                available = false;
            }
        } catch (Exception e) {
            logger.error("OpenAI 远程向量化服务初始化失败：{}", e.getMessage());
            logger.warn("将使用关键词匹配作为降级方案");
            available = false;
        }
    }
    
    @Override
    public float[] embed(String text) {
        if (!available || text == null || text.isBlank()) {
            return new float[dimension];
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            float[] embedding = callOpenAiEmbeddingApi(text);
            
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录指标
            if (metricsCollector != null) {
                metricsCollector.recordEmbedding(duration);
            }
            
            // 性能降级检查
            if (duration > PERFORMANCE_THRESHOLD_MS) {
                consecutiveSlowCalls++;
                if (consecutiveSlowCalls >= MAX_CONSECUTIVE_SLOW) {
                    logger.warn("连续 {} 次向量化耗时超过 {} ms，触发性能降级", 
                        consecutiveSlowCalls, PERFORMANCE_THRESHOLD_MS);
                    available = false;
                }
            } else {
                consecutiveSlowCalls = 0;
            }
            
            return embedding;
            
        } catch (Exception e) {
            logger.error("OpenAI 向量化失败：{}", e.getMessage());
            consecutiveSlowCalls++;
            
            // 记录指标
            if (metricsCollector != null) {
                metricsCollector.recordEmbedding(System.currentTimeMillis() - startTime);
            }
            
            return new float[dimension];
        }
    }
    
    @Override
    public float[][] embedBatch(String[] texts) {
        if (!available) {
            float[][] result = new float[texts.length][];
            for (int i = 0; i < texts.length; i++) {
                result[i] = new float[dimension];
            }
            return result;
        }
        
        try {
            // OpenAI API 支持批量向量化（最多 2048 条）
            if (texts.length <= 2048) {
                return callOpenAiEmbeddingApiBatch(texts);
            }
            
            // 分批处理
            float[][] result = new float[texts.length][];
            int batchSize = 100;
            for (int i = 0; i < texts.length; i += batchSize) {
                int end = Math.min(i + batchSize, texts.length);
                String[] batch = new String[end - i];
                System.arraycopy(texts, i, batch, 0, end - i);
                float[][] batchResult = callOpenAiEmbeddingApiBatch(batch);
                System.arraycopy(batchResult, 0, result, i, batchResult.length);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("批量向量化失败", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public int getDimension() {
        return dimension;
    }
    
    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        available = false;
    }
    
    /**
     * 调用 OpenAI Embedding API
     */
    private float[] callOpenAiEmbeddingApi(String text) throws IOException {
        String url = baseUrl + "/v1/embeddings";
        
        // 构建请求体
        String requestBody = objectMapper.writeValueAsString(
            new EmbeddingRequest(model, text, dimension)
        );
        
        Request request = new Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API 错误：" + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            return parseEmbeddingResponse(responseBody);
        }
    }
    
    /**
     * 批量调用 OpenAI Embedding API
     */
    private float[][] callOpenAiEmbeddingApiBatch(String[] texts) throws IOException {
        String url = baseUrl + "/v1/embeddings";
        
        // 构建请求体
        String requestBody = objectMapper.writeValueAsString(
            new EmbeddingRequest(model, texts, dimension)
        );
        
        Request request = new Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API 错误：" + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            return parseBatchEmbeddingResponse(responseBody);
        }
    }
    
    /**
     * 解析单条 Embedding 响应
     */
    private float[] parseEmbeddingResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        
        if (data == null || !data.isArray() || data.size() == 0) {
            throw new IOException("OpenAI 响应格式错误");
        }
        
        JsonNode embeddingNode = data.get(0).get("embedding");
        float[] embedding = new float[embeddingNode.size()];
        
        for (int i = 0; i < embeddingNode.size(); i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }
        
        return embedding;
    }
    
    /**
     * 解析批量 Embedding 响应
     */
    private float[][] parseBatchEmbeddingResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        
        if (data == null || !data.isArray()) {
            throw new IOException("OpenAI 响应格式错误");
        }
        
        float[][] embeddings = new float[data.size()][];
        
        for (int i = 0; i < data.size(); i++) {
            JsonNode embeddingNode = data.get(i).get("embedding");
            embeddings[i] = new float[embeddingNode.size()];
            
            for (int j = 0; j < embeddingNode.size(); j++) {
                embeddings[i][j] = (float) embeddingNode.get(j).asDouble();
            }
        }
        
        return embeddings;
    }
    
    /**
     * OpenAI Embedding API 请求体
     */
    private static class EmbeddingRequest {
        public String model;
        public Object input;
        public int dimensions;
        
        public EmbeddingRequest(String model, String input, int dimensions) {
            this.model = model;
            this.input = input;
            this.dimensions = dimensions;
        }
        
        public EmbeddingRequest(String model, String[] input, int dimensions) {
            this.model = model;
            this.input = input;
            this.dimensions = dimensions;
        }
    }
}
