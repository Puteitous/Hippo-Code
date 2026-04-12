package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.llm.retry.RetryPolicy;

/**
 * 保持向后兼容的默认客户端实现
 * 该类现在是 DashScopeLlmClient 的别名
 * 
 * @deprecated 建议使用 LlmClientFactory.create() 根据配置创建对应提供商的客户端
 */
@Deprecated
public class DefaultLlmClient extends DashScopeLlmClient {

    public DefaultLlmClient() {
        super();
    }

    public DefaultLlmClient(Config config) {
        super(config);
    }

    public DefaultLlmClient(Config config, RetryPolicy retryPolicy) {
        super(config, retryPolicy);
    }
}
