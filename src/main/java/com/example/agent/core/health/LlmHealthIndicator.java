package com.example.agent.core.health;

import com.example.agent.llm.client.LlmClient;
import com.example.agent.logging.CostMetricsCollector;

public class LlmHealthIndicator implements HealthIndicator {
    private final LlmClient llmClient;
    private final CostMetricsCollector costMetrics;

    public LlmHealthIndicator(LlmClient llmClient, CostMetricsCollector costMetrics) {
        this.llmClient = llmClient;
        this.costMetrics = costMetrics;
    }

    @Override
    public String getName() {
        return "llm";
    }

    @Override
    public Health check() {
        try {
            String provider = llmClient.getProviderName();
            String model = llmClient.getModel();

            return Health.up()
                    .withDetail("provider", provider)
                    .withDetail("model", model)
                    .withDetail("base_url", llmClient.getBaseUrl())
                    .withDetail("total_tokens", costMetrics.getTotalTokens())
                    .withDetail("total_cost_usd", costMetrics.getTotalCostUsd().toPlainString())
                    .withDetail("total_cost_cny", costMetrics.getTotalCostCny().toPlainString())
                    .build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
