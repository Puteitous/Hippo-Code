package com.example.agent.core.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HealthCheckRegistry {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckRegistry.class);

    private final Map<String, HealthIndicator> indicators = new ConcurrentHashMap<>();

    public void register(HealthIndicator indicator) {
        indicators.put(indicator.getName(), indicator);
        logger.debug("已注册健康检查器: {}", indicator.getName());
    }

    public void unregister(String name) {
        indicators.remove(name);
    }

    public Health checkAll() {
        Map<String, Health> results = new LinkedHashMap<>();
        HealthStatus overallStatus = HealthStatus.UP;
        List<String> degradedComponents = new ArrayList<>();
        List<String> downComponents = new ArrayList<>();

        for (Map.Entry<String, HealthIndicator> entry : indicators.entrySet()) {
            try {
                Health health = entry.getValue().check();
                results.put(entry.getKey(), health);

                switch (health.getStatus()) {
                    case DOWN:
                        overallStatus = HealthStatus.DOWN;
                        downComponents.add(entry.getKey());
                        break;
                    case DEGRADED:
                        if (overallStatus == HealthStatus.UP) {
                            overallStatus = HealthStatus.DEGRADED;
                        }
                        degradedComponents.add(entry.getKey());
                        break;
                    case UNKNOWN:
                        if (overallStatus == HealthStatus.UP) {
                            overallStatus = HealthStatus.UNKNOWN;
                        }
                        break;
                }
            } catch (Exception e) {
                logger.error("健康检查失败: {}", entry.getKey(), e);
                results.put(entry.getKey(), Health.down().withException(e).build());
                overallStatus = HealthStatus.DOWN;
                downComponents.add(entry.getKey());
            }
        }

        Health.Builder builder = Health.status(overallStatus)
                .withDetail("timestamp", Instant.now().toString())
                .withDetail("total_checks", indicators.size())
                .withDetail("components", getComponentStatus(results));

        if (!degradedComponents.isEmpty()) {
            builder.withDetail("degraded_components", degradedComponents);
        }
        if (!downComponents.isEmpty()) {
            builder.withDetail("down_components", downComponents);
        }

        return builder.build();
    }

    private Map<String, String> getComponentStatus(Map<String, Health> results) {
        Map<String, String> status = new LinkedHashMap<>();
        results.forEach((name, health) -> status.put(name, health.getStatus().name()));
        return status;
    }

    public Health check(String name) {
        HealthIndicator indicator = indicators.get(name);
        if (indicator == null) {
            return Health.unknown().withDetail("error", "健康检查器不存在: " + name).build();
        }
        try {
            return indicator.check();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }

    public List<String> getIndicatorNames() {
        return new ArrayList<>(indicators.keySet());
    }

    public String getReadableStatus() {
        Health health = checkAll();
        StringBuilder sb = new StringBuilder();

        sb.append("\n=== 🏥 系统健康检查 ===\n");
        sb.append(String.format("整体状态: %s %s%n", getStatusIcon(health.getStatus()), health.getStatus()));
        sb.append(String.format("检查时间: %s%n%n", health.getTimestamp()));

        sb.append("组件状态:\n");
        @SuppressWarnings("unchecked")
        Map<String, String> components = (Map<String, String>) health.getDetails().get("components");
        components.forEach((name, status) -> {
            Health detail = check(name);
            sb.append(String.format("  %s %-10s ", getStatusIcon(HealthStatus.valueOf(status)), name));
            if (detail.isUp()) {
                sb.append("✅ 正常");
            } else if (detail.getStatus() == HealthStatus.DEGRADED) {
                sb.append("⚠️ 降级");
            } else {
                sb.append("❌ 异常");
            }
            sb.append("\n");
        });

        return sb.toString();
    }

    private String getStatusIcon(HealthStatus status) {
        return switch (status) {
            case UP -> "🟢";
            case DEGRADED -> "🟡";
            case DOWN -> "🔴";
            case UNKNOWN -> "⚪";
        };
    }
}
