package com.example.agent.execute;

import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.model.Message;

import java.util.List;

public interface StopHook {

    StopHookResult evaluate(StopHookContext context);

    class StopHookContext {
        private final Conversation conversation;
        private final List<Message> recentMessages;
        private final int turnCount;
        private final AgentTurnResult lastResult;

        public StopHookContext(Conversation conversation, List<Message> recentMessages,
                               int turnCount, AgentTurnResult lastResult) {
            this.conversation = conversation;
            this.recentMessages = recentMessages;
            this.turnCount = turnCount;
            this.lastResult = lastResult;
        }

        public Conversation getConversation() {
            return conversation;
        }

        public List<Message> getRecentMessages() {
            return recentMessages;
        }

        public int getTurnCount() {
            return turnCount;
        }

        public AgentTurnResult getLastResult() {
            return lastResult;
        }
    }

    class StopHookResult {
        private final boolean shouldStop;
        private final String reason;
        private final boolean preventContinuation;
        private final boolean isWarning;

        private StopHookResult(boolean shouldStop, String reason, boolean preventContinuation, boolean isWarning) {
            this.shouldStop = shouldStop;
            this.reason = reason;
            this.preventContinuation = preventContinuation;
            this.isWarning = isWarning;
        }

        public static StopHookResult continueExecution() {
            return new StopHookResult(false, null, false, false);
        }

        public static StopHookResult stop(String reason) {
            return new StopHookResult(true, reason, true, false);
        }

        public static StopHookResult warn(String reason) {
            return new StopHookResult(false, reason, false, true);
        }

        public boolean isShouldStop() {
            return shouldStop;
        }

        public String getReason() {
            return reason;
        }

        public boolean isPreventContinuation() {
            return preventContinuation;
        }

        public boolean isWarning() {
            return isWarning;
        }
    }
}
