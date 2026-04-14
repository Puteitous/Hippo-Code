package com.example.agent.core.health;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

public class SystemHealthIndicator implements HealthIndicator {

    @Override
    public String getName() {
        return "system";
    }

    @Override
    public Health check() {
        try {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            int memoryUsagePercent = (int) (usedMemory * 100 / maxMemory);

            Health.Builder builder = Health.up()
                    .withDetail("os_name", os.getName())
                    .withDetail("os_arch", os.getArch())
                    .withDetail("available_processors", runtime.availableProcessors())
                    .withDetail("heap_used_mb", bytesToMb(usedMemory))
                    .withDetail("heap_max_mb", bytesToMb(maxMemory))
                    .withDetail("heap_usage_percent", memoryUsagePercent + "%")
                    .withDetail("non_heap_used_mb", bytesToMb(memory.getNonHeapMemoryUsage().getUsed()))
                    .withDetail("system_load_average", os.getSystemLoadAverage())
                    .withDetail("jvm_uptime_seconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

            if (memoryUsagePercent > 90) {
                return Health.degraded()
                        .withDetail("warning", "内存使用率超过90%")
                        .withDetail("heap_usage_percent", memoryUsagePercent + "%")
                        .build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }

    private long bytesToMb(long bytes) {
        return bytes / (1024 * 1024);
    }
}
