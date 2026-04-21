package com.example.agent.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CompactionMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(CompactionMetricsCollector.class);

    private final AtomicInteger totalCompactions = new AtomicInteger(0);
    private final AtomicInteger slidingWindowSuccess = new AtomicInteger(0);
    private final AtomicInteger llmSummaryFallback = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> fallbackReasons = new ConcurrentHashMap<>();

    public enum CompactionEvent {
        TENGU_SM_COMPACT_SUCCESS("tengu_sm_compact_success", "会话记忆压缩成功"),
        TENGU_SM_COMPACT_NO_SESSION_MEMORY("tengu_sm_compact_no_session_memory", "文件不存在"),
        TENGU_SM_COMPACT_EMPTY_TEMPLATE("tengu_sm_compact_empty_template", "文件存在但空模板"),
        TENGU_SM_COMPACT_RESUMED_SESSION("tengu_sm_compact_resumed_session", "恢复会话-边界重建"),
        TENGU_SM_COMPACT_THRESHOLD_EXCEEDED("tengu_sm_compact_threshold_exceeded", "压缩后仍超阈值"),
        TENGU_SM_COMPACT_SUMMARIZED_ID_NOT_FOUND("tengu_sm_compact_summarized_id_not_found", "边界ID不存在"),
        TENGU_SM_COMPACT_BOUNDARY_PERSISTED("tengu_sm_compact_boundary_persisted", "压缩边界已持久化"),
        TENGU_SM_COMPACT_TOOL_CALL_IN_PROGRESS("tengu_sm_compact_tool_call_in_progress", "存在未完成工具调用"),
        TENGU_SM_COMPACT_CIRCUIT_BREAKER("tengu_sm_compact_circuit_breaker", "断路器熔断"),
        TENGU_SM_COMPACT_ERROR("tengu_sm_compact_error", "压缩异常");

        private final String eventName;
        private final String description;

        CompactionEvent(String eventName, String description) {
            this.eventName = eventName;
            this.description = description;
        }

        public String getEventName() {
            return eventName;
        }

        public String getDescription() {
            return description;
        }
    }

    public void recordEvent(CompactionEvent event) {
        recordEvent(event, null);
    }

    public void recordEvent(CompactionEvent event, String details) {
        totalCompactions.incrementAndGet();

        switch (event) {
            case TENGU_SM_COMPACT_SUCCESS:
                slidingWindowSuccess.incrementAndGet();
                break;
            case TENGU_SM_COMPACT_NO_SESSION_MEMORY:
            case TENGU_SM_COMPACT_EMPTY_TEMPLATE:
            case TENGU_SM_COMPACT_RESUMED_SESSION:
            case TENGU_SM_COMPACT_THRESHOLD_EXCEEDED:
            case TENGU_SM_COMPACT_TOOL_CALL_IN_PROGRESS:
            case TENGU_SM_COMPACT_CIRCUIT_BREAKER:
            case TENGU_SM_COMPACT_ERROR:
                llmSummaryFallback.incrementAndGet();
                fallbackReasons.computeIfAbsent(event.getEventName(), k -> new AtomicInteger(0)).incrementAndGet();
                break;
        }

        if (details != null) {
            logger.info("[COMPACTION] {} - {} | {}", event.getEventName(), event.getDescription(), details);
        } else {
            logger.info("[COMPACTION] {} - {}", event.getEventName(), event.getDescription());
        }
    }

    public String getSummary() {
        int total = totalCompactions.get();
        int success = slidingWindowSuccess.get();
        int fallback = llmSummaryFallback.get();
        double successRate = total > 0 ? success * 100.0 / total : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                
                === 🗜️ 压缩统计 ===
                总压缩次数: %d
                  - 零成本滑动窗口: %d 次 (成功率: %.1f%%)
                  - LLM 摘要回退: %d 次
                """, total, success, successRate, fallback));

        if (!fallbackReasons.isEmpty()) {
            sb.append("\n  回退原因分布:\n");
            fallbackReasons.forEach((name, count) -> {
                CompactionEvent event = CompactionEvent.valueOf(name.toUpperCase()
                    .replace("tengu_sm_compact_", "")
                    .toUpperCase());
                sb.append(String.format("    - %s: %d 次\n", name, count.get()));
            });
        }

        return sb.toString();
    }
}
