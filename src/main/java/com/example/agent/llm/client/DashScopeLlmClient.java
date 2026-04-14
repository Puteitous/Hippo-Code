package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.retry.RetryPolicy;

import java.net.http.HttpRequest;
import java.util.List;

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

    @Override
    protected void enrichRequestHeaders(HttpRequest.Builder builder) {
        if (config.getLlm().isServerCache()) {
            builder.header("X-DashScope-Enable-Prompt-Cache", "true");
        }
    }

    @Override
    protected List<Message> applyCacheStrategy(List<Message> messages) {
        if (config.getLlm() == null || !config.getLlm().isServerCache()) {
            return messages;
        }
        
        logger.info("✨ 已启用 DashScope 服务端 Prompt 缓存（消息级标记）");
        
        int cachedCount = 0;
        for (Message msg : messages) {
            if (isEligibleForCache(msg)) {
                msg.enableEphemeralCache();
                cachedCount++;
            }
        }
        
        if (cachedCount > 0) {
            logger.debug("已标记 {} 条消息用于服务端缓存", cachedCount);
        }
        
        return messages;
    }

    private boolean isEligibleForCache(Message msg) {
        String role = msg.getRole();
        return ("system".equals(role) || "user".equals(role))
                && msg.getContent() != null
                && !msg.getContent().isEmpty();
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public String getDefaultModel() {
        return DEFAULT_MODEL;
    }

    public static String getDefaultBaseUrlStatic() {
        return DEFAULT_BASE_URL;
    }

    public static String getDefaultModelStatic() {
        return DEFAULT_MODEL;
    }

    @Override
    public String getProviderName() {
        return "dashscope";
    }
}
