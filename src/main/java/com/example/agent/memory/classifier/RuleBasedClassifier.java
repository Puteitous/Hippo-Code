package com.example.agent.memory.classifier;

import com.example.agent.llm.model.Message;
import com.example.agent.memory.model.MemoryPriority;
import com.example.agent.memory.model.MemoryTag;
import com.example.agent.memory.model.PrioritizedMessage;

import java.util.List;

public class RuleBasedClassifier implements MessageClassifier {

    private static final List<String> HIGH_PRIORITY_KEYWORDS = List.of(
            "必须", "注意", "重要", "要求", "需求", "不要", "记得", "一定要",
            "千万", "核心", "关键", "重点", "原则", "禁止", "确保"
    );

    private static final List<String> ERROR_KEYWORDS = List.of(
            "error", "exception", "错误", "失败", "异常", "堆栈", "stacktrace",
            "failed", "warning", "报错", "nullpointer", "npe"
    );

    @Override
    public PrioritizedMessage classify(Message message) {
        if (message == null) {
            return null;
        }

        String role = message.getRole();
        String content = message.getContent() != null ? message.getContent() : "";
        String contentLower = content.toLowerCase();

        if ("system".equals(role)) {
            return PrioritizedMessage.builder(message)
                    .priority(MemoryPriority.PINNED)
                    .tag(MemoryTag.SYSTEM_RULE)
                    .importance(100)
                    .build();
        }

        if ("user".equals(role)) {
            return classifyUserMessage(message, content, contentLower);
        }

        if ("tool".equals(role)) {
            return classifyToolMessage(message, content, contentLower);
        }

        if ("assistant".equals(role)) {
            return PrioritizedMessage.builder(message)
                    .priority(MemoryPriority.MEDIUM)
                    .importance(60)
                    .build();
        }

        return PrioritizedMessage.builder(message)
                .priority(MemoryPriority.MEDIUM)
                .build();
    }

    private PrioritizedMessage classifyUserMessage(Message message, String content, String contentLower) {
        boolean hasHighPriorityKeyword = HIGH_PRIORITY_KEYWORDS.stream()
                .anyMatch(k -> content.contains(k));

        if (hasHighPriorityKeyword) {
            return PrioritizedMessage.builder(message)
                    .priority(MemoryPriority.HIGH)
                    .tag(MemoryTag.USER_REQUIREMENT)
                    .importance(90)
                    .build();
        }

        return PrioritizedMessage.builder(message)
                .priority(MemoryPriority.MEDIUM)
                .tag(MemoryTag.USER_REQUIREMENT)
                .importance(75)
                .build();
    }

    private PrioritizedMessage classifyToolMessage(Message message, String content, String contentLower) {
        boolean hasError = ERROR_KEYWORDS.stream()
                .anyMatch(k -> contentLower.contains(k.toLowerCase()));

        if (hasError) {
            return PrioritizedMessage.builder(message)
                    .priority(MemoryPriority.HIGH)
                    .tag(MemoryTag.ERROR_INFO)
                    .importance(85)
                    .build();
        }

        if (content.length() > 3000) {
            return PrioritizedMessage.builder(message)
                    .priority(MemoryPriority.LOW)
                    .tag(MemoryTag.LARGE_CONTENT)
                    .compressible(true)
                    .importance(30)
                    .build();
        }

        if (content.length() > 1500) {
            return PrioritizedMessage.builder(message)
                    .priority(MemoryPriority.LOW)
                    .tag(MemoryTag.TOOL_RESULT)
                    .compressible(true)
                    .importance(40)
                    .build();
        }

        return PrioritizedMessage.builder(message)
                .priority(MemoryPriority.MEDIUM)
                .tag(MemoryTag.TOOL_RESULT)
                .importance(55)
                .build();
    }
}
