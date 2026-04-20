package com.example.agent.context;

import com.example.agent.context.compressor.AutoCompact;
import com.example.agent.context.compressor.DynamicSlidingWindow;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;

import java.util.List;

public class ManualCompactor {

    private final DynamicSlidingWindow slidingWindow;
    private final AutoCompact autoCompact;
    private final TokenEstimator tokenEstimator;

    public ManualCompactor(TokenEstimator tokenEstimator, LlmClient llmClient) {
        this.tokenEstimator = tokenEstimator;
        this.slidingWindow = new DynamicSlidingWindow(tokenEstimator);
        this.autoCompact = new AutoCompact(tokenEstimator, llmClient);
    }

    public CompactionResult compact(List<Message> messages, String userInstruction, int maxTokens) {
        int targetTokens = (int) (maxTokens * 0.7);

        boolean hasCustomInstruction = userInstruction != null 
            && !userInstruction.trim().isEmpty();

        if (hasCustomInstruction) {
            return doLLMCompress(messages, targetTokens, userInstruction);
        }

        CompactionResult windowResult = trySlidingWindow(messages, targetTokens, maxTokens);
        if (windowResult != null) {
            return windowResult;
        }

        return doLLMCompress(messages, targetTokens, null);
    }

    private CompactionResult trySlidingWindow(List<Message> messages, int targetTokens, int maxTokens) {
        long toolCount = messages.stream().filter(Message::isTool).count();
        if (toolCount <= 3 || messages.size() <= 15) {
            return null;
        }

        DynamicSlidingWindow.CompactionResult result = slidingWindow.compact(messages, targetTokens);
        
        int tokensAfter = tokenEstimator.estimateConversationTokens(result.getMessages());
        
        if (tokensAfter < (int)(maxTokens * 0.85)) {
            return new CompactionResult(
                result.getMessages(),
                CompactionMethod.SLIDING_WINDOW,
                result.getSavedTokens(),
                String.format("零成本压缩：保留 %d/%d 回合，释放 %d tokens",
                    result.getTotalTurns() - result.getRemovedTurns(),
                    result.getTotalTurns(),
                    result.getSavedTokens())
            );
        }

        return null;
    }

    private CompactionResult doLLMCompress(List<Message> messages, int targetTokens, String customInstruction) {
        if (customInstruction != null) {
            autoCompact.setCustomInstruction(customInstruction);
        }

        List<Message> result = autoCompact.compact(messages, targetTokens);
        AutoCompact.CompactionResult llmResult = autoCompact.getLastResult();

        return new CompactionResult(
            result,
            CompactionMethod.LLM_SUMMARY,
            llmResult.getTokenCountAfter(),
            customInstruction != null 
                ? String.format("自定义指令压缩：融合 %d 条消息", llmResult.getMergedCount())
                : String.format("智能摘要压缩：融合 %d 条消息为结构化摘要", llmResult.getMergedCount())
        );
    }

    public enum CompactionMethod {
        SLIDING_WINDOW("零成本滑动窗口"),
        LLM_SUMMARY("智能摘要压缩");

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
