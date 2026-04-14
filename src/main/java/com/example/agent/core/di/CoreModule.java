package com.example.agent.core.di;

import com.example.agent.config.Config;
import com.example.agent.domain.cache.CacheManager;
import com.example.agent.domain.index.CodeIndex;
import com.example.agent.domain.rule.RuleManager;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.client.LlmClientFactory;
import com.example.agent.llm.retry.RetryPolicy;
import com.example.agent.service.FileContentService;
import com.example.agent.service.TokenEstimator;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.tools.*;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;

public final class CoreModule {
    private CoreModule() {}

    public static void configure() {
        Config config = Config.getInstance();
        ServiceLocator.registerSingleton(Config.class, config);
        ServiceLocator.registerSingleton(CacheManager.class, CacheManager.getInstance());
        ServiceLocator.registerSingleton(RetryPolicy.class, RetryPolicy.defaultPolicy());

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
                new ConcurrentToolExecutor(ServiceLocator.get(ToolRegistry.class)));
    }

    private static ToolRegistry createConfiguredToolRegistry() {
        ToolRegistry registry = new ToolRegistry();

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
}