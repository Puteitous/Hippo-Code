package com.example.agent.execute;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RepetitionPatternHook implements StopHook {

    private static final Logger logger = LoggerFactory.getLogger(RepetitionPatternHook.class);
    private static final int MAX_CONSECUTIVE_REPETITION = 3;

    @Override
    public StopHook.StopHookResult evaluate(StopHook.StopHookContext context) {
        List<Message> messages = context.getRecentMessages();
        List<String> recentToolCalls = extractToolCallSignatures(messages);

        if (hasConsecutiveRepetition(recentToolCalls, MAX_CONSECUTIVE_REPETITION)) {
            String repeatedTool = recentToolCalls.get(recentToolCalls.size() - 1);
            String reason = String.format(
                "检测到重复模式：连续 %d 轮执行相同的工具调用 [%s]，强制终止循环",
                MAX_CONSECUTIVE_REPETITION,
                repeatedTool
            );
            logger.warn("RepetitionPatternHook 触发终止: {}", reason);
            return StopHook.StopHookResult.stop(reason);
        }

        return StopHook.StopHookResult.continueExecution();
    }

    private List<String> extractToolCallSignatures(List<Message> messages) {
        List<String> signatures = new ArrayList<>();

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.isAssistant() && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                String signature = buildToolCallSignature(msg.getToolCalls());
                signatures.add(0, signature);
            }
        }

        return signatures;
    }

    private String buildToolCallSignature(List<ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return "no_tool_calls";
        }

        List<String> toolNames = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            if (tc.getFunction() != null && tc.getFunction().getName() != null) {
                toolNames.add(tc.getFunction().getName());
            }
        }

        return String.join(",", toolNames);
    }

    private boolean hasConsecutiveRepetition(List<String> signatures, int threshold) {
        if (signatures.size() < threshold) {
            return false;
        }

        for (int i = signatures.size() - threshold; i >= 0; i--) {
            boolean allSame = true;
            String first = signatures.get(i);

            for (int j = 1; j < threshold; j++) {
                if (!first.equals(signatures.get(i + j))) {
                    allSame = false;
                    break;
                }
            }

            if (allSame) {
                return true;
            }
        }

        return false;
    }
}
