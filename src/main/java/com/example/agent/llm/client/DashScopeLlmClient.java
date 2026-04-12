package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.retry.RetryPolicy;

public class DashScopeLlmClient extends AbstractLlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String DEFAULT_MODEL = "qwen3.5-plus";

    public DashScopeLlmClient() {
        this(Config.getInstance());
    }

    public DashScopeLlmClient(Config config) {
        this(config, RetryPolicy.defaultPolicy());
    }

    public DashScopeLlmClient(Config config, RetryPolicy retryPolicy) {
        super(config, retryPolicy);
    }

    @Override
    protected String getChatCompletionsPath() {
        return CHAT_COMPLETIONS_PATH;
    }

    @Override
    protected String getAuthorizationHeader() {
        return "Bearer " + config.getApiKey();
    }

    public static String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    public static String getDefaultModel() {
        return DEFAULT_MODEL;
    }
}
