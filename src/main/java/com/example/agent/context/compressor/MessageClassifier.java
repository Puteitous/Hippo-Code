package com.example.agent.context.compressor;

import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.HashMap;
import java.util.Map;

public class MessageClassifier {

    private final TokenEstimator tokenEstimator;

    public MessageClassifier(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public ClassifiedMessage classify(Message message, int index, int totalMessages) {
        ClassifiedMessage classified = new ClassifiedMessage(message, index);
        
        classified.setTokenCount(tokenEstimator.estimateMessageTokens(message));
        classified.setMessageType(determineType(message));
        classified.setConversationCritical(isConversationCritical(message, index, totalMessages));
        classified.setValueScore(calculateValueScore(classified, index, totalMessages));
        
        return classified;
    }

    private double safeDivide(int numerator, int denominator) {
        if (denominator <= 0) {
            return 1.0;
        }
        return (double) numerator / denominator;
    }

    private MessageType determineType(Message message) {
        if (message.isSystem()) {
            return MessageType.SYSTEM;
        }
        if (message.isTool()) {
            return MessageType.TOOL_OUTPUT;
        }
        if (message.isAssistant() && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            return MessageType.TOOL_CALL;
        }
        if (message.isAssistant()) {
            return MessageType.REASONING;
        }
        if (message.isUser()) {
            return MessageType.USER_INPUT;
        }
        return MessageType.UNKNOWN;
    }

    private boolean isConversationCritical(Message message, int index, int totalMessages) {
        if (totalMessages <= 0) {
            return true;
        }
        int recencyThreshold = Math.max(6, totalMessages / 3);
        boolean isRecent = (totalMessages - index) <= recencyThreshold;

        if (message.isSystem()) {
            return true;
        }

        if (isRecent) {
            return true;
        }

        if (message.isUser() || (message.isAssistant() && message.getContent() != null && message.getContent().length() > 50)) {
            return containsCriticalContent(message.getContent());
        }

        return false;
    }

    private boolean containsCriticalContent(String content) {
        if (content == null) {
            return false;
        }
        String lower = content.toLowerCase();
        return lower.contains("bug")
            || lower.contains("fix")
            || lower.contains("error")
            || lower.contains("important")
            || lower.contains("关键")
            || lower.contains("必须")
            || lower.contains("记住")
            || lower.contains("note:")
            || lower.contains("remember:");
    }

    private double calculateValueScore(ClassifiedMessage classified, int index, int totalMessages) {
        double score = 0.0;
        double recency = safeDivide(index + 1, totalMessages);

        score += recency * 0.4;

        switch (classified.getMessageType()) {
            case SYSTEM:
                score += 1.0;
                break;
            case USER_INPUT:
                score += 0.8;
                break;
            case REASONING:
                score += 0.7;
                break;
            case TOOL_CALL:
                score += 0.4;
                break;
            case TOOL_OUTPUT:
                score += 0.2;
                break;
            default:
                score += 0.1;
        }

        if (classified.isConversationCritical()) {
            score += 0.5;
        }

        int tokens = classified.getTokenCount();
        if (tokens > 2000) {
            score -= 0.3;
        }

        return score;
    }

    public enum MessageType {
        SYSTEM,
        USER_INPUT,
        REASONING,
        TOOL_CALL,
        TOOL_OUTPUT,
        UNKNOWN
    }

    public static class ClassifiedMessage {
        private final Message message;
        private final int originalIndex;
        private MessageType messageType;
        private boolean conversationCritical;
        private int tokenCount;
        private double valueScore;

        public ClassifiedMessage(Message message, int originalIndex) {
            this.message = message;
            this.originalIndex = originalIndex;
        }

        public Message getMessage() {
            return message;
        }

        public int getOriginalIndex() {
            return originalIndex;
        }

        public MessageType getMessageType() {
            return messageType;
        }

        public void setMessageType(MessageType messageType) {
            this.messageType = messageType;
        }

        public boolean isConversationCritical() {
            return conversationCritical;
        }

        public void setConversationCritical(boolean conversationCritical) {
            this.conversationCritical = conversationCritical;
        }

        public int getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(int tokenCount) {
            this.tokenCount = tokenCount;
        }

        public double getValueScore() {
            return valueScore;
        }

        public void setValueScore(double valueScore) {
            this.valueScore = valueScore;
        }

        public boolean isRemovable() {
            return !conversationCritical && messageType == MessageType.TOOL_OUTPUT;
        }
    }
}
