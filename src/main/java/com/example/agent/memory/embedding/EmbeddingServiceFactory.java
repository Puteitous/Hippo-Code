package com.example.agent.memory.embedding;

import com.example.agent.config.Config;
import com.example.agent.memory.MemoryMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 向量化服务工厂
 * 
 * 根据配置创建合适的 EmbeddingService 实例
 * 支持本地 ONNX 模型和 OpenAI API 两种模式
 */
public class EmbeddingServiceFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingServiceFactory.class);
    private static final String DEFAULT_EMBEDDING_PROVIDER = "local";
    private static final String DEFAULT_LOCAL_MODEL_DIR = ".hippo/models/embedding";
    private static final String DEFAULT_OPENAI_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_OPENAI_DIMENSION = 1536;
    
    public enum Provider {
        LOCAL,
        OPENAI,
        DISABLED
    }
    
    /**
     * 从配置创建 EmbeddingService
     */
    public static EmbeddingService create(Config config, MemoryMetricsCollector metricsCollector) {
        if (config == null) {
            logger.warn("配置为 null，使用默认本地向量化服务");
            return createDefault(metricsCollector);
        }
        
        String providerName = config.getEmbedding() != null && config.getEmbedding().getProvider() != null
            ? config.getEmbedding().getProvider()
            : DEFAULT_EMBEDDING_PROVIDER;
        
        Provider provider = parseProvider(providerName);
        
        logger.info("创建向量化服务：provider={}", providerName);
        
        switch (provider) {
            case LOCAL:
                return createLocalService(config, metricsCollector);
            case OPENAI:
                return createOpenAiService(config, metricsCollector);
            case DISABLED:
                logger.info("向量化服务已禁用，将使用关键词匹配");
                return createDisabledService();
            default:
                logger.warn("未知的向量化提供商：{}，使用默认本地服务", providerName);
                return createDefault(metricsCollector);
        }
    }
    
    /**
     * 创建默认本地服务
     */
    public static EmbeddingService createDefault(MemoryMetricsCollector metricsCollector) {
        Path modelDir = Paths.get(DEFAULT_LOCAL_MODEL_DIR);
        return new LocalEmbeddingService(modelDir, metricsCollector);
    }
    
    /**
     * 创建本地 ONNX 服务
     */
    private static EmbeddingService createLocalService(Config config, MemoryMetricsCollector metricsCollector) {
        String modelDir = config.getEmbedding() != null && config.getEmbedding().getModelDir() != null
            ? config.getEmbedding().getModelDir()
            : DEFAULT_LOCAL_MODEL_DIR;
        
        Path modelPath = Paths.get(modelDir);
        logger.info("创建本地 ONNX 向量化服务，模型目录：{}", modelPath.toAbsolutePath());
        
        return new LocalEmbeddingService(modelPath, metricsCollector);
    }
    
    /**
     * 创建 OpenAI 远程服务
     */
    private static EmbeddingService createOpenAiService(Config config, MemoryMetricsCollector metricsCollector) {
        String apiKey = config.getEmbedding() != null && config.getEmbedding().getApiKey() != null
            ? config.getEmbedding().getApiKey()
            : config.getLlm().getApiKey();
        
        String baseUrl = config.getEmbedding() != null && config.getEmbedding().getBaseUrl() != null
            ? config.getEmbedding().getBaseUrl()
            : "https://api.openai.com";
        
        String model = config.getEmbedding() != null && config.getEmbedding().getModel() != null
            ? config.getEmbedding().getModel()
            : DEFAULT_OPENAI_EMBEDDING_MODEL;
        
        int dimension = config.getEmbedding() != null && config.getEmbedding().getDimension() > 0
            ? config.getEmbedding().getDimension()
            : DEFAULT_OPENAI_DIMENSION;
        
        logger.info("创建 OpenAI 远程向量化服务，模型：{}，维度：{}", model, dimension);
        
        return new OpenAiEmbeddingService(apiKey, baseUrl, model, dimension, metricsCollector);
    }
    
    /**
     * 创建禁用服务（占位实现）
     */
    public static EmbeddingService createDisabledService() {
        return new EmbeddingService() {
            @Override
            public float[] embed(String text) {
                return new float[384];
            }
            
            @Override
            public float[][] embedBatch(String[] texts) {
                return new float[texts.length][];
            }
            
            @Override
            public boolean isAvailable() {
                return false;
            }
            
            @Override
            public int getDimension() {
                return 384;
            }
            
            @Override
            public void close() {
            }
        };
    }
    
    /**
     * 解析提供商名称
     */
    private static Provider parseProvider(String name) {
        if (name == null) {
            return Provider.LOCAL;
        }
        
        switch (name.toLowerCase().trim()) {
            case "local":
            case "onnx":
                return Provider.LOCAL;
            case "openai":
            case "open_ai":
                return Provider.OPENAI;
            case "disabled":
            case "none":
            case "keyword":
                return Provider.DISABLED;
            default:
                logger.warn("未知的向量化提供商：{}，使用默认本地服务", name);
                return Provider.LOCAL;
        }
    }
}
