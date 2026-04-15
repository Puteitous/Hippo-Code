package com.example.agent.memory;

import com.example.agent.context.TrimPolicy;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.classifier.MessageClassifier;
import com.example.agent.memory.classifier.RuleBasedClassifier;
import com.example.agent.memory.model.MemoryPriority;
import com.example.agent.memory.model.PrioritizedMessage;
import com.example.agent.memory.summarizer.ExtractiveSummarizer;
import com.example.agent.memory.summarizer.SummaryGenerator;
import com.example.agent.service.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PriorityTrimPolicy implements TrimPolicy {

    private static final Logger logger = LoggerFactory.getLogger(PriorityTrimPolicy.class);

    private final TokenEstimator tokenEstimator;
    private final MessageClassifier classifier;
    private final SummaryGenerator summarizer;
    private final boolean summaryEnabled;
    private final int recentTurnsToKeep = 8;

    public PriorityTrimPolicy(TokenEstimator tokenEstimator) {
        this(tokenEstimator, true);
    }

    public PriorityTrimPolicy(TokenEstimator tokenEstimator, boolean summaryEnabled) {
        this.tokenEstimator = tokenEstimator;
        this.classifier = new RuleBasedClassifier();
        this.summarizer = new ExtractiveSummarizer();
        this.summaryEnabled = summaryEnabled;
    }

    @Override
    public List<Message> apply(List<Message> messages, int maxTokens, int maxMessages) {
        if (messages == null || messages.size() <= 2) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        logger.debug("开始优先级截断: {} 条消息, 上限 {} tokens, {} 条",
                messages.size(), maxTokens, maxMessages);

        List<PrioritizedMessage> prioritized = classifyAndSort(messages);
        List<Message> result = applyPriorityRetention(prioritized, maxTokens, maxMessages);

        logger.debug("优先级截断完成: {} → {} 条消息, 约 {} tokens",
                messages.size(), result.size(), tokenEstimator.estimateConversationTokens(result));

        return result;
    }

    private List<PrioritizedMessage> classifyAndSort(List<Message> messages) {
        List<PrioritizedMessage> prioritized = new ArrayList<>();
        for (Message msg : messages) {
            PrioritizedMessage pm = classifier.classify(msg);
            if (pm != null) {
                prioritized.add(pm);
            }
        }
        prioritized.sort(Comparator.comparingInt(PrioritizedMessage::calculateRetentionScore).reversed());
        return prioritized;
    }

    private List<Message> applyPriorityRetention(List<PrioritizedMessage> prioritized,
                                                 int maxTokens, int maxMessages) {
        List<Message> result = new ArrayList<>();
        List<PrioritizedMessage> toSummarize = new ArrayList<>();

        // 🔴 第一步：PINNED 消息全部保留
        for (PrioritizedMessage pm : prioritized) {
            if (pm.getPriority() == MemoryPriority.PINNED) {
                result.add(pm.getMessage());
            }
        }

        // 🟢 第二步：HIGH 优先级消息全部保留
        for (PrioritizedMessage pm : prioritized) {
            if (pm.getPriority() == MemoryPriority.HIGH) {
                result.add(pm.getMessage());
            }
        }

        // 检查是否已超 Token 限制
        int currentTokens = tokenEstimator.estimateConversationTokens(result);
        logger.debug("保留 PINNED + HIGH 后 tokens: {}", currentTokens);

        // 第三步：分离 MEDIUM 历史，保留最近 N 轮，更早的生成摘要
        if (summaryEnabled && currentTokens < maxTokens * 0.7) {
            List<PrioritizedMessage> mediumMessages = new ArrayList<>();
            List<PrioritizedMessage> lowMessages = new ArrayList<>();

            for (PrioritizedMessage pm : prioritized) {
                if (pm.getPriority() == MemoryPriority.MEDIUM) {
                    mediumMessages.add(pm);
                } else if (pm.getPriority() == MemoryPriority.LOW
                        || pm.getPriority() == MemoryPriority.EPHEMERAL) {
                    lowMessages.add(pm);
                }
            }

            int keepCount = Math.min(recentTurnsToKeep, mediumMessages.size());
            for (int i = 0; i < keepCount; i++) {
                result.add(mediumMessages.get(mediumMessages.size() - 1 - i).getMessage());
            }

            if (mediumMessages.size() > recentTurnsToKeep) {
                toSummarize.addAll(mediumMessages.subList(0, mediumMessages.size() - recentTurnsToKeep));
            }

            toSummarize.addAll(lowMessages);

            if (!toSummarize.isEmpty() && toSummarize.size() > 5) {
                Message summary = summarizer.generate(toSummarize);
                if (summary != null) {
                    result.add(1, summary);
                    logger.debug("已生成历史摘要，覆盖 {} 条消息", toSummarize.size());
                }
            }
        }

        trimToTokenLimit(result, maxTokens);

        return result;
    }

    private void trimToTokenLimit(List<Message> result, int maxTokens) {
        while (result.size() > 1) {
            int totalTokens = tokenEstimator.estimateConversationTokens(result);
            if (totalTokens <= maxTokens) {
                break;
            }
            int removedIndex = findLowestPriorityRemovableIndex(result);
            if (removedIndex > 0) {
                result.remove(removedIndex);
            } else {
                break;
            }
        }
    }

    private int findLowestPriorityRemovableIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 1; i--) {
            Message msg = messages.get(i);
            PrioritizedMessage pm = classifier.classify(msg);
            if (pm != null && pm.getPriority() != MemoryPriority.PINNED
                    && pm.getPriority() != MemoryPriority.HIGH) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getName() {
        return "PriorityTrimPolicy";
    }

    public boolean isSummaryEnabled() {
        return summaryEnabled;
    }
}
