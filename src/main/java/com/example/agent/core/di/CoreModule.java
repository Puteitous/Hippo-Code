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
        Config config = Config.getInstance();
        ServiceLocator.registerSingleton(Config.class, config);
        ServiceLocator.registerSingleton(CacheManager.class, CacheManager.getInstance());
        ServiceLocator.registerSingleton(RetryPolicy.class, RetryPolicy.defaultPolicy());
        ServiceLocator.registerSingleton(ObjectMapper.class, createConfiguredObjectMapper());
        logger.info("全局 ObjectMapper 配置完成 ✅");

        ThreadPools.initialize();
        logger.info("全局线程池管理器初始化完成 ✅");

        CostMetricsCollector costMetrics = new CostMetricsCollector();
        ServiceLocator.registerSingleton(CostMetricsCollector.class, costMetrics);
        logger.info("LLM 成本计算器初始化完成 ✅ (事件驱动模式)");

        HealthCheckRegistry healthRegistry = new HealthCheckRegistry();
        healthRegistry.register(new SystemHealthIndicator());
        healthRegistry.register(new ConfigHealthIndicator(config));
        healthRegistry.register(new CacheHealthIndicator(ServiceLocator.get(CacheManager.class)));
        ServiceLocator.registerSingleton(HealthCheckRegistry.class, healthRegistry);
        logger.info("健康检查注册中心初始化完成 ✅ (共 {} 个检查器)", healthRegistry.getIndicatorNames().size());

        ServiceLocator.registerProvider(LlmHealthIndicator.class, () ->
                new LlmHealthIndicator(
                        ServiceLocator.get(LlmClient.class),
                        ServiceLocator.get(CostMetricsCollector.class)));

        ServiceLocator.registerProvider(TokenEstimator.class, () ->
                TokenEstimatorFactory.create(ServiceLocator.get(Config.class)));

        ServiceLocator.registerProvider(LlmClient.class, () ->
                LlmClientFactory.create(
                        ServiceLocator.get(Config.class),
                        ServiceLocator.get(RetryPolicy.class)));

        ServiceLocator.registerProvider(RuleManager.class, () ->
                new RuleManager(
                        ServiceLocator.get(TokenEstimator.class),
                        ServiceLocator.get(Config.class).getRule()));

        ServiceLocator.registerProvider(FileContentService.class, () ->
                new FileContentService(ServiceLocator.get(CacheManager.class)));

        ServiceLocator.registerProvider(CodeIndex.class, () ->
                new CodeIndex(
                        ServiceLocator.get(TokenEstimator.class),
                        ServiceLocator.get(Config.class).getIndex(),
                        ServiceLocator.get(CacheManager.class)));

        ServiceLocator.registerProvider(ToolRegistry.class, CoreModule::createConfiguredToolRegistry);

        ServiceLocator.registerProvider(ConcurrentToolExecutor.class, () ->
                new ConcurrentToolExecutor(
                        ServiceLocator.get(ToolRegistry.class),
                        ServiceLocator.get(ObjectMapper.class)
                ));

        ServiceLocator.registerProvider(ThinkingEngine.class, () ->
                new ThinkingEngine(
                        ServiceLocator.get(LlmClient.class),
                        ServiceLocator.get(ToolRegistry.class),
                        ServiceLocator.get(ConcurrentToolExecutor.class),
                        ServiceLocator.get(ObjectMapper.class)
                ));

        healthRegistry.register(ServiceLocator.get(LlmHealthIndicator.class));
        logger.info("LLM 健康检查器已注册 ✅");
    }

    private static ToolRegistry createConfiguredToolRegistry() {
        ToolRegistry registry = new ToolRegistry(ServiceLocator.get(ObjectMapper.class));

        registry.register(new ReadFileTool(ServiceLocator.get(FileContentService.class)));
        registry.register(new WriteFileTool(ServiceLocator.get(CacheManager.class)));
        registry.register(new EditFileTool(ServiceLocator.get(CacheManager.class)));
        registry.register(new SearchCodeTool(ServiceLocator.get(CodeIndex.class)));

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