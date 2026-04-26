package com.example.agent.subagent;

import java.util.List;

public class SubAgentResultFormatter {

    private static final String HEADER_TEMPLATE = 
        "═══════════════════════════════════════════════════════════════\n" +
        "                        EXECUTION RESULT                       \n" +
        "═══════════════════════════════════════════════════════════════\n";

    private static final String FOOTER_TEMPLATE = 
        "\n═══════════════════════════════════════════════════════════════\n" +
        "                        END OF RESULT                          \n" +
        "═══════════════════════════════════════════════════════════════\n";

    private static final String SECTION_SEPARATOR = 
        "───────────────────────────────────────────────────────────────\n";

    private SubAgentResultFormatter() {
    }

    public static String formatSingleResult(SubAgentTask task) {
        StringBuilder sb = new StringBuilder(HEADER_TEMPLATE);
        
        sb.append(formatTaskHeader(task));
        sb.append(SECTION_SEPARATOR);
        sb.append(formatStatus(task));
        sb.append(SECTION_SEPARATOR);
        sb.append(formatTiming(task));
        sb.append(SECTION_SEPARATOR);
        sb.append(formatResult(task));
        
        sb.append(FOOTER_TEMPLATE);
        return sb.toString();
    }

    public static String formatBatchResults(List<SubAgentTask> tasks) {
        StringBuilder sb = new StringBuilder(HEADER_TEMPLATE);
        
        sb.append("📊 BATCH PARALLEL EXECUTION SUMMARY\n");
        sb.append("   Total Tasks    : ").append(tasks.size()).append("\n");
        long forkCount = tasks.stream().filter(SubAgentTask::isForkChild).count();
        sb.append("   Fork Optimized : ").append(forkCount).append("\n");
        sb.append("   Cache Savings  : ~").append(calculateCacheSavings(tasks)).append("%\n");
        
        for (int i = 0; i < tasks.size(); i++) {
            SubAgentTask task = tasks.get(i);
            sb.append(SECTION_SEPARATOR);
            sb.append("\n📌 TASK #").append(i + 1).append("\n");
            sb.append(formatTaskHeader(task));
            sb.append(formatStatus(task));
            sb.append(formatTiming(task));
            sb.append(formatResult(task));
        }
        
        sb.append(FOOTER_TEMPLATE);
        return sb.toString();
    }

    private static String formatTaskHeader(SubAgentTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Task ID    : ").append(task.getTaskId()).append("\n");
        sb.append("🔍 Mode       : ").append(task.isForkChild() ? "FORK CACHE OPTIMIZED" : "STANDALONE").append("\n");
        return sb.toString();
    }

    private static String formatStatus(SubAgentTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ Status     : ");
        switch (task.getStatus()) {
            case COMPLETED:
                sb.append("✅ SUCCESS");
                break;
            case FAILED:
                sb.append("❌ FAILED");
                break;
            case CANCELLED:
                sb.append("⏱️ TIMEOUT / CANCELLED");
                break;
            default:
                sb.append("🔄 ").append(task.getStatus());
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatTiming(SubAgentTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("⏱️ Duration   : ");
        if (task.getStatus() == SubAgentStatus.COMPLETED || task.getStatus() == SubAgentStatus.FAILED) {
            long ms = java.time.Duration.between(task.getCreatedAt(), java.time.Instant.now()).toMillis();
            sb.append(String.format("%.2f seconds", ms / 1000.0));
        } else {
            sb.append("In Progress");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatResult(SubAgentTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n📝 RESULT SUMMARY:\n");
        sb.append("──────────────────\n");
        if (task.getResultSummary() != null && !task.getResultSummary().isBlank()) {
            sb.append(task.getResultSummary()).append("\n");
        } else if (task.getError() != null) {
            sb.append("ERROR: ").append(task.getError().getMessage()).append("\n");
        } else {
            sb.append("Task completed successfully.\n");
        }
        return sb.toString();
    }

    private static int calculateCacheSavings(List<SubAgentTask> tasks) {
        long forkCount = tasks.stream().filter(SubAgentTask::isForkChild).count();
        if (tasks.isEmpty()) return 0;
        return (int) (forkCount * 98L / tasks.size());
    }
}
