package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class ContextAwareBlocker implements Blocker {

    private static final Logger logger = LoggerFactory.getLogger(ContextAwareBlocker.class);

    private static final Set<String> HIGH_RISK_TOOLS = Set.of(
            "bash", "write_file", "edit_file"
    );

    private static final Map<String, Integer> TOOL_RISK_WEIGHTS = Map.of(
            "bash", 10,
            "write_file", 7,
            "edit_file", 5,
            "delete_file", 8,
            "fork_agent", 6
    );

    private static final int HIGH_RISK_THRESHOLD = 8;

    private int consecutiveHighRiskOps = 0;
    private int totalOperations = 0;

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        totalOperations++;

        int riskScore = calculateRiskScore(toolName, arguments);

        if (riskScore >= HIGH_RISK_THRESHOLD) {
            consecutiveHighRiskOps++;
        } else {
            consecutiveHighRiskOps = 0;
        }

        if (riskScore >= HIGH_RISK_THRESHOLD) {
            logger.warn("[风险提醒] 工具={} 风险等级={} 连续高风险操作={} 总操作数={}",
                    toolName, riskScore, consecutiveHighRiskOps, totalOperations);
        }

        return HookResult.allow();
    }

    private int calculateRiskScore(String toolName, JsonNode arguments) {
        int baseScore = TOOL_RISK_WEIGHTS.getOrDefault(toolName, 1);

        if ("bash".equals(toolName) && arguments.has("command")) {
            String command = arguments.get("command").asText();
            baseScore += calculateBashCommandRiskScore(command);
        }

        if ("edit_file".equals(toolName) && arguments.has("path")) {
            String path = arguments.get("path").asText();
            baseScore += calculatePathRiskScore(path);
        }

        if ("write_file".equals(toolName) && arguments.has("path")) {
            String path = arguments.get("path").asText();
            baseScore += calculatePathRiskScore(path);
        }

        return baseScore;
    }

    private int calculateBashCommandRiskScore(String command) {
        int score = 0;

        if (command.contains("sudo") || command.contains("su ")) {
            score += 5;
        }

        if (command.contains("rm ") || command.contains("del ")) {
            score += 4;
        }

        if (command.contains("chmod") || command.contains("chown")) {
            score += 3;
        }

        if (command.contains("wget") || command.contains("curl")) {
            score += 2;
        }

        if (command.contains("pip install") || command.contains("npm install")) {
            score += 2;
        }

        return score;
    }

    private int calculatePathRiskScore(String path) {
        int score = 0;

        if (path.contains("/.") || path.contains("\\")) {
            score += 2;
        }

        if (path.startsWith("/") || path.matches("^[A-Z]:\\\\")) {
            score += 1;
        }

        if (path.contains("config") || path.contains(".env") || path.contains("secret")) {
            score += 3;
        }

        return score;
    }

    public void reset() {
        consecutiveHighRiskOps = 0;
        totalOperations = 0;
    }

    public int getConsecutiveHighRiskOps() {
        return consecutiveHighRiskOps;
    }

    public int getTotalOperations() {
        return totalOperations;
    }
}
