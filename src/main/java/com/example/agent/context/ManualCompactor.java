package com.example.agent.context;

import com.example.agent.context.compressor.ContextClipper;
import com.example.agent.context.compressor.ContextSummarizer;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;

public class ManualCompactor {

    private final ContextClipper clipper;
    private final ContextSummarizer summarizer;
    private final TokenEstimator tokenEstimator;

    public ManualCompactor(TokenEstimator tokenEstimator, LlmClient llmClient) {
        this.tokenEstimator = tokenEstimator;
        this.clipper = new ContextClipper(tokenEstimator);
        this.summarizer = new ContextSummarizer(tokenEstimator, llmClient);
    }

    public CompactionResult compact(List<Message> messages, String userInstruction, int maxTokens) {
        int targetTokens = (int) (maxTokens * 0.7);

        boolean hasCustomInstruction = userInstruction != null 
            && !userInstruction.trim().isEmpty();

        if (hasCustomInstruction) {
            return doSummary(messages, targetTokens, userInstruction);
        }

        CompactionResult windowResult = tryClipping(messages, targetTokens, maxTokens);
        if (windowResult != null) {
            return windowResult;
        }

        return doSummary(messages, targetTokens, null);
    }

    private CompactionResult tryClipping(List<Message> messages, int targetTokens, int maxTokens) {
        long toolCount = messages.stream().filter(Message::isTool).count();
        if (toolCount <= 3 || messages.size() <= 15) {
            return null;
        }

        ContextClipper.CompactionResult result = clipper.compact(messages, targetTokens);
        
        int tokensAfter = tokenEstimator.estimateConversationTokens(result.getMessages());
        
        if (tokensAfter < (int)(maxTokens * 0.85)) {
            return new CompactionResult(
                result.getMessages(),
                CompactionMethod.CLIPPING,
                result.getSavedTokens(),
                String.format("零成本裁剪：保留 %d/%d 回合，释放 %d tokens",
                    result.getTotalTurns() - result.getRemovedTurns(),
                    result.getTotalTurns(),
                    result.getSavedTokens())
            );
        }

        return null;
    }

    private CompactionResult doSummary(List<Message> messages, int targetTokens, String customInstruction) {
        if (customInstruction != null) {
            summarizer.setCustomInstruction(customInstruction);
        }

        List<Message> result = summarizer.compact(messages, targetTokens);
        ContextSummarizer.CompactionResult llmResult = summarizer.getLastResult();

        return new CompactionResult(
            result,
            CompactionMethod.SUMMARY,
            llmResult.getTokenCountAfter(),
            customInstruction != null 
                ? String.format("自定义指令压缩：融合 %d 条消息", llmResult.getMergedCount())
                : String.format("智能摘要：融合 %d 条消息为结构化摘要", llmResult.getMergedCount())
        );
    }

    public enum CompactionMethod {
        CLIPPING("零成本裁剪"),
        SUMMARY("智能摘要");

        private final String displayName;

        CompactionMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class CompactionResult {
        private final List<Message> messages;
        private final CompactionMethod method;
        private final int savedTokens;
        private final String summary;

        public CompactionResult(List<Message> messages, CompactionMethod method, int savedTokens, String summary) {
            this.messages = messages;
            this.method = method;
            this.savedTokens = savedTokens;
            this.summary = summary;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public CompactionMethod getMethod() {
            return method;
        }

        public int getSavedTokens() {
            return savedTokens;
        }

        public String getSummary() {
            return summary;
        }
    }
}
