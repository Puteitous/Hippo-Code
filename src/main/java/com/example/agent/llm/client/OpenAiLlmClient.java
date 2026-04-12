package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.retry.RetryPolicy;

public class OpenAiLlmClient extends AbstractLlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o";

    public OpenAiLlmClient() {
        this(Config.getInstance());
    }

    public OpenAiLlmClient(Config config) {
        this(config, RetryPolicy.defaultPolicy());
    }

    public OpenAiLlmClient(Config config, RetryPolicy retryPolicy) {
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
