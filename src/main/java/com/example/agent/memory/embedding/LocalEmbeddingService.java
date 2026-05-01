package com.example.agent.memory.embedding;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.agent.memory.MemoryMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 本地 ONNX 向量化实现
 * 
 * 使用 BGE-small-zh-v1.5 模型（ONNX 格式）
 * 如果模型不可用，自动降级到关键词匹配
 */
public class LocalEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalEmbeddingService.class);
    private static final int DIMENSION = 512;
    private static final int MAX_SEQ_LENGTH = 512;
    private static final String MODEL_DIR = ".hippo/models/embedding";
    private static final String MODEL_FILE = "model.onnx";
    private static final long PERFORMANCE_THRESHOLD_MS = 200;
    private static final int MAX_CONSECUTIVE_SLOW = 3;
    
    private boolean available = false;
    private int consecutiveSlowCalls = 0;
    private Path modelPath;
    private MemoryMetricsCollector metricsCollector;
    
    // ONNX Runtime 资源
    private OrtEnvironment ortEnv;
    private OrtSession session;
    
    public LocalEmbeddingService() {
        this(Paths.get(MODEL_DIR), null);
    }
    
    public LocalEmbeddingService(Path customModelPath) {
        this(customModelPath, null);
    }
    
    public LocalEmbeddingService(Path customModelPath, MemoryMetricsCollector metricsCollector) {
        this.modelPath = customModelPath;
        this.metricsCollector = metricsCollector;
        initialize();
    }
    
    private void initialize() {
        Path modelFile = modelPath.resolve(MODEL_FILE);
        
        if (!Files.exists(modelFile)) {
            logger.warn("本地向量化模型不存在：{}", modelFile);
            logger.warn("将使用关键词匹配作为降级方案");
            logger.warn("请下载 BGE-small-zh-v1.5 ONNX 模型到：{}", modelFile.toAbsolutePath());
            available = false;
            return;
        }
        
        try {
            ortEnv = OrtEnvironment.getEnvironment();
            
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            
            session = ortEnv.createSession(modelFile.toString(), options);
            
            logger.info("✅ 本地向量化服务初始化完成（ONNX Runtime）");
            logger.info("   模型路径：{}", modelFile.toAbsolutePath());
            logger.info("   向量维度：{}", DIMENSION);
            logger.info("   最大序列长度：{}", MAX_SEQ_LENGTH);
            available = true;
            
        } catch (Exception e) {
            logger.error("本地向量化服务初始化失败：{}", e.getMessage());
            logger.warn("将使用关键词匹配作为降级方案");
            available = false;
        }
    }
    
    @Override
    public float[] embed(String text) {
        if (!available || text == null || text.isBlank()) {
            return new float[DIMENSION];
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            float[] embedding = runInference(text);
            
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
            logger.error("向量化失败：{}", e.getMessage());
            consecutiveSlowCalls++;
            
            // 记录指标
            if (metricsCollector != null) {
                metricsCollector.recordEmbedding(System.currentTimeMillis() - startTime);
            }
            
            return new float[DIMENSION];
        }
    }
    
    @Override
    public float[][] embedBatch(String[] texts) {
        float[][] embeddings = new float[texts.length][];
        for (int i = 0; i < texts.length; i++) {
            embeddings[i] = embed(texts[i]);
        }
        return embeddings;
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public int getDimension() {
        return DIMENSION;
    }
    
    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            logger.error("关闭 ONNX Session 失败", e);
        }
        available = false;
    }
    
    /**
     * 运行 ONNX 推理
     * 
     * BGE-small 模型输入：
     * - input_ids: [batch_size, seq_length]
     * - attention_mask: [batch_size, seq_length]
     * 
     * 输出：
     * - last_hidden_state: [batch_size, seq_length, hidden_size]
     * 
     * 使用 [CLS] token 的隐藏状态作为句子向量
     */
    private float[] runInference(String text) throws OrtException {
        // 1. 简单分词（字符级 + 特殊 token）
        // 注意：这是简化实现，生产环境应使用 HuggingFace tokenizer
        long[] inputIds = tokenize(text);
        long[] attentionMask = new long[inputIds.length];
        Arrays.fill(attentionMask, 1);
        
        // 2. 创建输入张量
        long[] shape = {1, inputIds.length};
        
        try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(ortEnv, 
                new long[][]{inputIds});
             OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(ortEnv, 
                new long[][]{attentionMask})) {
            
            Map<String, OnnxTensor> inputs = Map.of(
                "input_ids", inputIdsTensor,
                "attention_mask", attentionMaskTensor
            );
            
            // 3. 运行推理
            try (OrtSession.Result result = session.run(inputs)) {
                // 4. 提取 [CLS] token 的隐藏状态
                float[][][] hiddenStates = (float[][][]) result.get(0).getValue();
                
                // [CLS] token 是第一个 token
                float[] embedding = hiddenStates[0][0];
                
                // 5. L2 归一化
                normalize(embedding);
                
                return embedding;
            }
        }
    }
    
    /**
     * 简单分词实现
     * 
     * 注意：这是简化版本，生产环境应使用完整的 BERT tokenizer
     * 包括 WordPiece 分词、词汇表查找等
     */
    private long[] tokenize(String text) {
        List<Long> tokens = new ArrayList<>();
        
        // [CLS] token ID = 101
        tokens.add(101L);
        
        // 简单字符级分词（UTF-8 字节）
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            // 将字节映射到词汇表 ID（简化实现）
            tokens.add((long) (b & 0xFF) + 1000);
        }
        
        // [SEP] token ID = 102
        tokens.add(102L);
        
        // 截断到最大长度
        if (tokens.size() > MAX_SEQ_LENGTH) {
            tokens = tokens.subList(0, MAX_SEQ_LENGTH);
            // 确保最后一个 token 是 [SEP]
            tokens.set(tokens.size() - 1, 102L);
        }
        
        return tokens.stream().mapToLong(Long::longValue).toArray();
    }
    
    /**
     * L2 归一化
     */
    private void normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) norm;
            }
        }
    }
}
