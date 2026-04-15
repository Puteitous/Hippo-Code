package com.example.agent.orchestrator.executor;

import com.example.agent.orchestrator.model.RetryPolicy;
import com.example.agent.orchestrator.model.ToolNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RetryHandler {

    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);

    private static final Map<String, RetryPolicy> DEFAULT_POLICIES = Map.ofEntries(
            Map.entry("read_file", new RetryPolicy(3, 1000, true)),
            Map.entry("SearchCodebase", new RetryPolicy(2, 500, true)),
            Map.entry("grep", new RetryPolicy(2, 500, true)),
            Map.entry("glob", new RetryPolicy(2, 500, true)),
            Map.entry("ls", new RetryPolicy(2, 300, true)),
            Map.entry("edit_file", new RetryPolicy(1, 0, false)),
            Map.entry("write_file", new RetryPolicy(1, 0, false)),
            Map.entry("delete_file", new RetryPolicy(1, 0, false)),
            Map.entry("bash", new RetryPolicy(1, 0, false))
    );

    private final Map<String, RetryPolicy> policies;

    public RetryHandler() {
        this(DEFAULT_POLICIES);
    }

    public RetryHandler(Map<String, RetryPolicy> policies) {
        this.policies = policies;
    }

    public RetryPolicy getPolicy(String toolName) {
        return policies.getOrDefault(toolName, RetryPolicy.NO_RETRY);
    }

    public boolean shouldRetry(ToolNode node, Exception e) {
        RetryPolicy policy = getPolicy(node.getToolName());
        if (!policy.shouldRetry()) {
            return false;
        }
        if (node.getRetryCount() >= policy.getMaxRetries()) {
            return false;
        }
        return isRetriableException(e);
    }

    public void awaitRetry(ToolNode node) throws InterruptedException {
        RetryPolicy policy = getPolicy(node.getToolName());
        int delay = policy.getDelayMs();
        if (delay > 0) {
            logger.warn("工具 {} 执行失败，{}ms 后重试 ({}/{})",
                    node.getToolName(), delay,
                    node.getRetryCount() + 1, policy.getMaxRetries());
            TimeUnit.MILLISECONDS.sleep(delay);
        }
        node.incrementRetry();
    }

    private boolean isRetriableException(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("timeout")
                || message.contains("connection")
                || message.contains("网络")
                || message.contains("暂时不可用")
                || e instanceof java.util.concurrent.TimeoutException;
    }
}
