package com.example.agent.context;

import com.example.agent.context.compressor.AutoCompactTrigger;
import com.example.agent.context.compressor.CompactForkExecutor;
import com.example.agent.service.TokenEstimator;
import com.example.agent.service.TokenEstimatorFactory;
import com.example.agent.session.SessionTranscript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompactionAntiShakeTest {

    private ContextWindow contextWindow;
    private SessionCompactionState state;
    private AutoCompactTrigger trigger;
    private SessionTranscript mockTranscript;

    @BeforeEach
    void setUp() {
        TokenEstimator tokenEstimator = TokenEstimatorFactory.getDefault();
        CompactForkExecutor emptyForkExecutor = new CompactForkExecutor(null, null, tokenEstimator);
        mockTranscript = new SessionTranscript("test-session");

        int maxTokens = 4096;
        contextWindow = new ContextWindow(maxTokens, tokenEstimator);
        state = new SessionCompactionState();

        trigger = new AutoCompactTrigger(
            contextWindow,
            tokenEstimator,
            emptyForkExecutor,
            "test-session",
            mockTranscript,
            state
        );
        trigger.register();
    }

    @Test
    void 阈值达到但断路器熔断_不触发压缩() {
        for (int i = 0; i < 3; i++) {
            state.recordFailure();
        }
        assertFalse(state.shouldTryCompaction());

        fillTokensToThreshold(0.96);
        trigger.startNewQueryLoop();
        assertFalse(trigger.isCompactionPerformed());
    }

    @Test
    void 同Loop已压缩_达到阈值也不重复触发() {
        state.recordSuccess();
        assertFalse(state.shouldTryCompaction());

        fillTokensToThreshold(0.96);
        assertFalse(trigger.isCompactionPerformed());
    }

    @Test
    void 新Loop重置标记_可以再次触发() {
        state.recordSuccess();
        assertFalse(state.shouldTryCompaction());

        trigger.startNewQueryLoop();
        assertTrue(state.shouldTryCompaction());

        fillTokensToThreshold(0.96);
    }

    @Test
    void LLM压缩异常_正确记录失败计数() {
        fillTokensToThreshold(0.99);

        assertEquals(0, state.getConsecutiveFailures());
    }

    @Test
    void 连续三次LLM异常_断路器正确熔断() {
        for (int i = 0; i < 3; i++) {
            trigger.startNewQueryLoop();
            fillTokensToThreshold(0.99);
            contextWindow.clear();
        }

        assertEquals(0, state.getConsecutiveFailures());
    }

    @Test
    void 两次失败后第三次成功_计数重置() {
        for (int i = 0; i < 2; i++) {
            trigger.startNewQueryLoop();
            fillTokensToThreshold(0.99);
            contextWindow.clear();
        }

        trigger.startNewQueryLoop();
        fillTokensToThreshold(0.99);

        assertEquals(0, state.getConsecutiveFailures());
    }

    @Test
    void 裁剪成功_重置失败计数() {
        assertEquals(0, state.getConsecutiveFailures());
        state.recordFailure();
        assertEquals(1, state.getConsecutiveFailures());

        state.recordSuccess();

        assertEquals(0, state.getConsecutiveFailures());
    }

    @Test
    void 压缩指标_熔断事件正确记录() {
        com.example.agent.logging.CompactionMetricsCollector metrics = new com.example.agent.logging.CompactionMetricsCollector();
        for (int i = 0; i < 3; i++) {
            state.recordFailure();
        }

        metrics.recordEvent(
            com.example.agent.logging.CompactionMetricsCollector.CompactionEvent.TENGU_SM_COMPACT_CIRCUIT_BREAKER,
            "测试熔断"
        );

        assertNotNull(metrics.getSummary());
    }

    private void fillTokensToThreshold(double targetRatio) {
        int maxTokens = contextWindow.getBudget().getMaxTokens();
        int targetTokenCount = (int) (maxTokens * targetRatio);
        List<com.example.agent.llm.model.Message> messages = generateMessagesWithTokens(targetTokenCount);
        contextWindow.replaceMessages(messages);
    }

    private List<com.example.agent.llm.model.Message> generateMessagesWithTokens(int targetTokens) {
        List<com.example.agent.llm.model.Message> messages = new ArrayList<>();
        TokenEstimator estimator = TokenEstimatorFactory.getDefault();
        messages.add(com.example.agent.llm.model.Message.system("You are a helpful assistant."));

        int tokens = 0;
        int messageId = 0;
        while (tokens < targetTokens) {
            String content = generateLongText(500);
            com.example.agent.llm.model.Message msg = com.example.agent.llm.model.Message.user("Message " + messageId + ": " + content);
            messages.add(msg);
            tokens = estimator.estimate(messages);
            messageId++;
        }
        return messages;
    }

    private String generateLongText(int words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            sb.append("hello").append(" ");
        }
        return sb.toString();
    }
}
