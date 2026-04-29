package com.example.agent.core.health;

import com.example.agent.config.Config;

public class ConfigHealthIndicator implements HealthIndicator {
    private final Config config;

    public ConfigHealthIndicator(Config config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public Health check() {
        try {
            boolean hasApiKey = config.getLlm().getApiKey() != null && !config.getLlm().getApiKey().isBlank();
            boolean hasModel = config.getLlm().getModel() != null && !config.getLlm().getModel().isBlank();
            boolean hasBaseUrl = config.getLlm().getBaseUrl() != null && !config.getLlm().getBaseUrl().isBlank();

            Health.Builder builder = Health.up()
                    .withDetail("model", config.getLlm().getModel())
                    .withDetail("base_url", config.getLlm().getBaseUrl())
                    .withDetail("api_key_configured", hasApiKey)
                    .withDetail("mcp_enabled", config.getMcp().isEnabled())
                    .withDetail("mcp_servers", config.getMcp().getServers().size())
                    .withDetail("max_tokens", config.getLlm().getMaxTokens())
                    .withDetail("temperature", config.getLlm().getTemperature());

            if (!hasApiKey || !hasModel || !hasBaseUrl) {
                return Health.degraded()
                        .withDetail("warning", "必要配置缺失")
                        .withDetail("has_api_key", hasApiKey)
                        .withDetail("has_model", hasModel)
                        .withDetail("has_base_url", hasBaseUrl)
                        .build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
