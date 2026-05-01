package com.example.agent.memory;

import com.example.agent.config.Config;
import com.example.agent.memory.embedding.EmbeddingService;
import com.example.agent.memory.embedding.EmbeddingServiceFactory;
import com.example.agent.memory.embedding.LocalEmbeddingService;
import com.example.agent.memory.embedding.OpenAiEmbeddingService;
import com.example.agent.domain.rule.HippoRulesParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 记忆模块初始化器
 * 
 * 负责：
 * 1. 启动时主备切换自动化检测
 * 2. 创建 MemoryStore、EmbeddingService、MemoryRetriever
 * 3. 注册到 DI 容器
 */
public class MemoryModule {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryModule.class);
    
    private static MemoryStore memoryStore;
    private static EmbeddingService embeddingService;
    private static MemoryRetriever memoryRetriever;
    private static MemoryMetricsCollector metricsCollector;
    
    private MemoryModule() {}
    
    /**
     * 初始化记忆模块
     * 
     * @param config 应用配置
     * @param memoryRoot 记忆存储根目录
     * @return MemoryRetriever 实例
     */
    public static MemoryRetriever initialize(Config config, Path memoryRoot) {
        logger.info("========== 初始化记忆模块 ==========");
        
        // 1. 创建指标收集器
        metricsCollector = new MemoryMetricsCollector();
        
        // 2. 创建沙箱和存储
        MemoryToolSandbox sandbox = new MemoryToolSandbox(memoryRoot);
        memoryStore = new MemoryStore(sandbox);
        logger.info("✅ MemoryStore 初始化完成，当前索引大小：{}", memoryStore.getIndexSize());
        
        // 3. 创建 EmbeddingService（带主备切换）
        embeddingService = createEmbeddingServiceWithFallback(config);
        
        // 4. 创建检索器（自动设置维度感知）
        HippoRulesParser rulesParser = new HippoRulesParser();
        rulesParser.loadFromWorkspace();
        memoryRetriever = new MemoryRetriever(memoryStore, rulesParser, embeddingService, metricsCollector);
        
        // 5. 注册到 DI 容器
        com.example.agent.core.di.ServiceLocator.registerSingleton(MemoryStore.class, memoryStore);
        com.example.agent.core.di.ServiceLocator.registerSingleton(EmbeddingService.class, embeddingService);
        com.example.agent.core.di.ServiceLocator.registerSingleton(MemoryRetriever.class, memoryRetriever);
        com.example.agent.core.di.ServiceLocator.registerSingleton(MemoryMetricsCollector.class, metricsCollector);
        
        // 6. 注册记忆工具到 ToolRegistry
        registerMemoryTools();
        
        logger.info("========== 记忆模块初始化完成 ==========");
        
        return memoryRetriever;
    }
    
    /**
     * 创建 EmbeddingService（带主备切换自动化）
     * 
     * 启动时检测主服务可用性，不可用时自动切换到备选服务
     */
    private static EmbeddingService createEmbeddingServiceWithFallback(Config config) {
        String providerName = config.getEmbedding() != null && config.getEmbedding().getProvider() != null
            ? config.getEmbedding().getProvider()
            : "local";
        
        logger.info("向量化提供商配置：{}", providerName);
        
        // 根据配置创建主服务
        EmbeddingService primaryService = EmbeddingServiceFactory.create(config, metricsCollector);
        
        // 启动时可用性检测
        if (primaryService.isAvailable()) {
            logger.info("✅ 主向量化服务可用：{}", providerName);
            return primaryService;
        }
        
        // 主服务不可用，尝试备选方案
        logger.warn("⚠️ 主向量化服务不可用：{}", providerName);
        
        if ("local".equalsIgnoreCase(providerName)) {
            // 本地模型不可用，尝试 OpenAI API
            logger.info("尝试切换到 OpenAI API 作为备选...");
            EmbeddingService openAiService = tryCreateOpenAiService(config);
            if (openAiService != null && openAiService.isAvailable()) {
                logger.info("✅ 成功切换到 OpenAI API 备选服务");
                return openAiService;
            }
        } else if ("openai".equalsIgnoreCase(providerName)) {
            // OpenAI 不可用，尝试本地模型
            logger.info("尝试切换到本地 ONNX 模型作为备选...");
            EmbeddingService localService = tryCreateLocalService(config);
            if (localService != null && localService.isAvailable()) {
                logger.info("✅ 成功切换到本地 ONNX 模型备选服务");
                return localService;
            }
        }
        
        // 所有方案都不可用，返回禁用服务
        logger.warn("⚠️ 所有向量化服务均不可用，将使用关键词匹配作为降级方案");
        return EmbeddingServiceFactory.createDisabledService();
    }
    
    /**
     * 尝试创建 OpenAI 服务
     */
    private static EmbeddingService tryCreateOpenAiService(Config config) {
        try {
            String apiKey = config.getEmbedding() != null && config.getEmbedding().getApiKey() != null
                ? config.getEmbedding().getApiKey()
                : config.getLlm().getApiKey();
            
            if (apiKey == null || apiKey.isBlank()) {
                logger.warn("OpenAI API Key 未配置，无法切换到 OpenAI 备选服务");
                return null;
            }
            
            String baseUrl = config.getEmbedding() != null && config.getEmbedding().getBaseUrl() != null
                ? config.getEmbedding().getBaseUrl()
                : "https://api.openai.com";
            
            String model = config.getEmbedding() != null && config.getEmbedding().getModel() != null
                ? config.getEmbedding().getModel()
                : "text-embedding-3-small";
            
            int dimension = config.getEmbedding() != null && config.getEmbedding().getDimension() > 0
                ? config.getEmbedding().getDimension()
                : 1536;
            
            return new OpenAiEmbeddingService(apiKey, baseUrl, model, dimension, metricsCollector);
        } catch (Exception e) {
            logger.warn("创建 OpenAI 备选服务失败：{}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 尝试创建本地服务
     */
    private static EmbeddingService tryCreateLocalService(Config config) {
        try {
            String modelDir = config.getEmbedding() != null && config.getEmbedding().getModelDir() != null
                ? config.getEmbedding().getModelDir()
                : ".hippo/models/embedding";
            
            return new LocalEmbeddingService(Paths.get(modelDir), metricsCollector);
        } catch (Exception e) {
            logger.warn("创建本地 ONNX 备选服务失败：{}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 注册记忆工具到 ToolRegistry
     */
    private static void registerMemoryTools() {
        try {
            com.example.agent.tools.ToolRegistry toolRegistry = 
                com.example.agent.core.di.ServiceLocator.get(com.example.agent.tools.ToolRegistry.class);
            
            toolRegistry.register(new com.example.agent.tools.SearchMemoryTool(memoryStore, embeddingService));
            toolRegistry.register(new com.example.agent.tools.RecallMemoryTool(memoryStore));
            
            logger.info("✅ 记忆工具已注册：search_memory, recall_memory");
        } catch (Exception e) {
            logger.warn("注册记忆工具失败（ToolRegistry 可能未初始化）：{}", e.getMessage());
        }
    }

    // Getter 方法
    
    public static MemoryStore getMemoryStore() {
        return memoryStore;
    }
    
    public static EmbeddingService getEmbeddingService() {
        return embeddingService;
    }
    
    public static MemoryRetriever getMemoryRetriever() {
        return memoryRetriever;
    }
    
    public static MemoryMetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
}
