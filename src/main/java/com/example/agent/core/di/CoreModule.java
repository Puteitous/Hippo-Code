package com.example.agent.core.di;

import com.example.agent.config.Config;
import com.example.agent.core.ThinkingEngine;
import com.example.agent.core.concurrency.ThreadPools;
import com.example.agent.core.health.CacheHealthIndicator;
import com.example.agent.core.health.ConfigHealthIndicator;
import com.example.agent.core.health.HealthCheckRegistry;
import com.example.agent.core.health.LlmHealthIndicator;
import com.example.agent.core.health.SystemHealthIndicator;
import com.example.agent.domain.cache.CacheManager;
import com.example.agent.domain.index.CodeIndex;
import com.example.agent.domain.rule.RuleManager;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.client.LlmClientFactory;
import com.example.agent.llm.retry.RetryPolicy;
import com.example.agent.logging.CostMetricsCollector;
import com.example.agent.service.FileContentService;
import com.example.agent.service.TokenEstimator;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.tools.*;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoreModule {
    private static final Logger logger = LoggerFactory.getLogger(CoreModule.class);

    private CoreModule() {}

    public static void configure() {
        logger.info("========== 按层级初始化 DI 容器 ==========");

        Config config = Config.getInstance();

        ThreadPools.initialize();
        logger.info("✅ [Level 0] 基础设施: 线程池管理器");

        ObjectMapper objectMapper = createConfiguredObjectMapper();
        ServiceLocator.registerSingleton(ObjectMapper.class, objectMapper);
        logger.info("✅ [Level 0] 基础设施: ObjectMapper");

        ServiceLocator.registerSingleton(Config.class, config);
        ServiceLocator.registerSingleton(CacheManager.class, CacheManager.getInstance());
        ServiceLocator.registerSingleton(RetryPolicy.class, RetryPolicy.defaultPolicy());
        logger.info("✅ [Level 1] 基础服务: Config, CacheManager, RetryPolicy");

        TokenEstimator tokenEstimator = TokenEstimatorFactory.create(config);
        ServiceLocator.registerSingleton(TokenEstimator.class, tokenEstimator);
        logger.info("✅ [Level 1] 基础服务: TokenEstimator");

        CostMetricsCollector costMetrics = new CostMetricsCollector();
        ServiceLocator.registerSingleton(CostMetricsCollector.class, costMetrics);
        logger.info("✅ [Level 1] 基础服务: 成本计算器");

        HealthCheckRegistry healthRegistry = new HealthCheckRegistry();
        healthRegistry.register(new SystemHealthIndicator());
        healthRegistry.register(new ConfigHealthIndicator(config));
        healthRegistry.register(new CacheHealthIndicator(ServiceLocator.get(CacheManager.class)));
        ServiceLocator.registerSingleton(HealthCheckRegistry.class, healthRegistry);
        logger.info("✅ [Level 1] 基础服务: 健康检查注册中心 ({} 个检查器)", healthRegistry.getIndicatorNames().size());

        RuleManager ruleManager = new RuleManager(tokenEstimator, config.getRule());
        ServiceLocator.registerSingleton(RuleManager.class, ruleManager);
        logger.info("✅ [Level 2] 领域服务: RuleManager");

        FileContentService fileContentService = new FileContentService(ServiceLocator.get(CacheManager.class));
        ServiceLocator.registerSingleton(FileContentService.class, fileContentService);
        logger.info("✅ [Level 2] 领域服务: FileContentService");

        CodeIndex codeIndex = new CodeIndex(tokenEstimator, config.getIndex(), ServiceLocator.get(CacheManager.class));
        ServiceLocator.registerSingleton(CodeIndex.class, codeIndex);
        logger.info("✅ [Level 2] 领域服务: CodeIndex");

        LlmClient llmClient = LlmClientFactory.create(config, ServiceLocator.get(RetryPolicy.class));
        ServiceLocator.registerSingleton(LlmClient.class, llmClient);
        logger.info("✅ [Level 2] 领域服务: LlmClient");

        ToolRegistry toolRegistry = createConfiguredToolRegistry(objectMapper, fileContentService, codeIndex);
        ServiceLocator.registerSingleton(ToolRegistry.class, toolRegistry);
        logger.info("✅ [Level 3] 工具层: ToolRegistry (9 个内置工具)");

        ConcurrentToolExecutor concurrentToolExecutor = new ConcurrentToolExecutor(toolRegistry, objectMapper);
        ServiceLocator.registerSingleton(ConcurrentToolExecutor.class, concurrentToolExecutor);
        logger.info("✅ [Level 3] 工具层: ConcurrentToolExecutor");

        ThinkingEngine thinkingEngine = new ThinkingEngine(llmClient, toolRegistry, concurrentToolExecutor, objectMapper);
        ServiceLocator.registerSingleton(ThinkingEngine.class, thinkingEngine);
        logger.info("✅ [Level 4] 引擎层: ThinkingEngine");

        healthRegistry.register(new LlmHealthIndicator(llmClient, costMetrics));
        logger.info("✅ [收尾] LLM 健康检查器已注册");

        logger.info("========== DI 容器初始化完成，共 {} 个服务 ==========", ServiceLocator.countSingletons());
    }

    private static ToolRegistry createConfiguredToolRegistry(ObjectMapper objectMapper,
                                                             FileContentService fileContentService,
                                                             CodeIndex codeIndex) {
        CacheManager cacheManager = ServiceLocator.get(CacheManager.class);
        ToolRegistry registry = new ToolRegistry(objectMapper);

        registry.getBlockerChain().add(new com.example.agent.core.blocker.EditCountBlocker());

        registry.register(new ReadFileTool(fileContentService));
        registry.register(new WriteFileTool(cacheManager));
        registry.register(new EditFileTool(cacheManager));
        registry.register(new SearchCodeTool(codeIndex));

        registry.register(new ListDirectoryTool());
        registry.register(new GlobTool());
        registry.register(new GrepTool());
        registry.register(new AskUserTool());
        registry.register(new BashTool());

        return registry;
    }

    private static ObjectMapper createConfiguredObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
