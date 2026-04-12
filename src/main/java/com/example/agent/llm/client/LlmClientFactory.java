package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class LlmClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(LlmClientFactory.class);

    public enum Provider {
        DASHSCOPE,
        OPENAI,
        OLLAMA,
        AZURE,
        ANTHROPIC,
        CUSTOM
    }

    private LlmClientFactory() {
    }

    public static LlmClient create() {
        return create(Config.getInstance());
    }

    public static LlmClient create(Config config) {
        return create(config, RetryPolicy.defaultPolicy());
    }

    public static LlmClient create(Config config, RetryPolicy retryPolicy) {
        if (config == null) {
            throw new IllegalArgumentException("Config不能为null");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("RetryPolicy不能为null");
        }

        applyProviderDefaults(config);
        
        String providerName = config.getLlm().getProvider() != null ? config.getLlm().getProvider() : "dashscope";
        Provider provider = parseProvider(providerName);

        logger.info("创建 LLM 客户端: provider={}, model={}, baseUrl={}", 
            providerName, config.getModel(), config.getBaseUrl());

        switch (provider) {
            case DASHSCOPE:
                return new DashScopeLlmClient(config, retryPolicy);
            case OPENAI:
                return new OpenAiLlmClient(config, retryPolicy);
            case OLLAMA:
                return new OllamaLlmClient(config, retryPolicy);
            case CUSTOM:
                return createCustomClient(config, retryPolicy);
            case AZURE:
            case ANTHROPIC:
            default:
                logger.warn("提供商 {} 尚未完全支持，使用 OpenAI 兼容模式", provider);
                return new OpenAiLlmClient(config, retryPolicy);
        }
    }

    private static void applyProviderDefaults(Config config) {
        String providerName = config.getLlm().getProvider() != null ? config.getLlm().getProvider() : "dashscope";
        
        if (config.getLlm().getBaseUrl() == null || config.getLlm().getBaseUrl().isEmpty()) {
            config.getLlm().setBaseUrl(getDefaultBaseUrl(providerName));
        }
        if (config.getLlm().getModel() == null || config.getLlm().getModel().isEmpty()) {
            config.getLlm().setModel(getDefaultModel(providerName));
        }
    }

    private static Provider parseProvider(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            return Provider.DASHSCOPE;
        }

        String normalized = providerName.trim().toUpperCase(Locale.ROOT);
        
        switch (normalized) {
            case "DASHSCOPE":
            case "ALIYUN":
            case "QWEN":
                return Provider.DASHSCOPE;
            case "OPENAI":
            case "GPT":
                return Provider.OPENAI;
            case "OLLAMA":
            case "LOCAL":
                return Provider.OLLAMA;
            case "AZURE":
            case "AZURE_OPENAI":
                return Provider.AZURE;
            case "ANTHROPIC":
            case "CLAUDE":
                return Provider.ANTHROPIC;
            case "CUSTOM":
                return Provider.CUSTOM;
            default:
                logger.warn("未知的 LLM 提供商: {}，默认使用 DASHSCOPE", providerName);
                return Provider.DASHSCOPE;
        }
    }

    private static LlmClient createCustomClient(Config config, RetryPolicy retryPolicy) {
        logger.info("使用自定义 OpenAI 兼容模式: baseUrl={}, model={}", 
            config.getBaseUrl(), config.getModel());
        return new OpenAiLlmClient(config, retryPolicy);
    }

    public static String getDefaultBaseUrl(String providerName) {
        Provider provider = parseProvider(providerName);
        switch (provider) {
            case DASHSCOPE:
                return DashScopeLlmClient.getDefaultBaseUrl();
            case OPENAI:
                return OpenAiLlmClient.getDefaultBaseUrl();
            case OLLAMA:
                return OllamaLlmClient.getDefaultBaseUrl();
            default:
                return OpenAiLlmClient.getDefaultBaseUrl();
        }
    }

    public static String getDefaultModel(String providerName) {
        Provider provider = parseProvider(providerName);
        switch (provider) {
            case DASHSCOPE:
                return DashScopeLlmClient.getDefaultModel();
            case OPENAI:
                return OpenAiLlmClient.getDefaultModel();
            case OLLAMA:
                return OllamaLlmClient.getDefaultModel();
            default:
                return OpenAiLlmClient.getDefaultModel();
        }
    }
}
