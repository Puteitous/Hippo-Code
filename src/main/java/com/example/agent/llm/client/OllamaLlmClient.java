package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.retry.RetryPolicy;

public class OllamaLlmClient extends AbstractLlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "qwen2.5:7b";

    public OllamaLlmClient() {
        this(Config.getInstance());
    }

    public OllamaLlmClient(Config config) {
        this(config, RetryPolicy.defaultPolicy());
    }

    public OllamaLlmClient(Config config, RetryPolicy retryPolicy) {
        super(config, retryPolicy);
    }

    @Override
    protected String getChatCompletionsPath() {
        return CHAT_COMPLETIONS_PATH;
    }

    @Override
    protected String getAuthorizationHeader() {
        return null;
    }

    public static String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    public static String getDefaultModel() {
        return DEFAULT_MODEL;
    }
}
