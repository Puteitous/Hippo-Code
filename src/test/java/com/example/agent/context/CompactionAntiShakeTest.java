package com.example.agent.context;

import com.example.agent.context.compressor.AutoCompactTrigger;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.logging.CompactionMetricsCollector;
import com.example.agent.service.TokenEstimator;
import com.example.agent.session.SessionTranscript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class CompactionAntiShakeTest {

    private ContextWindow contextWindow;
    private SessionCompactionState state;
    private AutoCompactTrigger trigger;
    private LlmClient mockLlmClient;
    private SessionTranscript mockTranscript;

    @BeforeEach
    void setUp() {
        TokenEstimator tokenEstimator = new TokenEstimator();
        mockLlmClient = Mockito.mock(LlmClient.class);
        mockTranscript = Mockito.mock(SessionTranscript.class);

        int maxTokens = 4096;
        contextWindow = new ContextWindow(maxTokens, tokenEstimator);
        state = new SessionCompactionState();

        trigger = new AutoCompactTrigger(
            contextWindow,
            tokenEstimator,
            mockLlmClient,
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
        when(mockLlmClient.chat(any(), any()))
            .thenThrow(new RuntimeException("LLM API 超时"));

        fillTokensToThreshold(0.99);

        assertEquals(1, state.getConsecutiveFailures());
        assertTrue(state.shouldTryCompaction());
    }

    @Test
    void 连续三次LLM异常_断路器正确熔断() {
        when(mockLlmClient.chat(any(), any()))
            .thenThrow(new RuntimeException("LLM API 超时"));

        for (int i = 0; i < 3; i++) {
            trigger.startNewQueryLoop();
            fillTokensToThreshold(0.99);
            contextWindow.clear();
            reset(mockLlmClient);
            when(mockLlmClient.chat(any(), any()))
                .thenThrow(new RuntimeException("LLM API 超时"));
        }

        assertEquals(3, state.getConsecutiveFailures());
        assertFalse(state.shouldTryCompaction());

        trigger.startNewQueryLoop();
        fillTokensToThreshold(0.99);
        assertFalse(state.shouldTryCompaction());
    }

    @Test
    void 两次失败后第三次成功_计数重置() {
        when(mockLlmClient.chat(any(), any()))
            .thenThrow(new RuntimeException("LLM API 超时"))
            .thenThrow(new RuntimeException("LLM API 超时"))
            .thenReturn(createStubCompactionResponse());

        for (int i = 0; i < 2; i++) {
            trigger.startNewQueryLoop();
            fillTokensToThreshold(0.99);
            contextWindow.clear();
        }

        trigger.startNewQueryLoop();
        fillTokensToThreshold(0.99);

        assertEquals(0, state.getConsecutiveFailures());
        assertFalse(state.shouldTryCompaction());
    }

    @Test
    void 裁剪成功_重置失败计数() {
        assertEquals(0, state.getConsecutiveFailures());
        state.recordFailure();
        assertEquals(1, state.getConsecutiveFailures());

        fillTokensToThreshold(0.92);

        assertEquals(0, state.getConsecutiveFailures());
    }

    @Test
    void 压缩指标_熔断事件正确记录() {
        CompactionMetricsCollector metrics = new CompactionMetricsCollector();
        for (int i = 0; i < 3; i++) {
            state.recordFailure();
        }

        metrics.recordEvent(
            CompactionMetricsCollector.CompactionEvent.TENGU_SM_COMPACT_CIRCUIT_BREAKER,
            "测试熔断"
        );

        assertTrue(metrics.getSummaryStats().contains("circuit_breaker"));
    }

    private void fillTokensToThreshold(double targetRatio) {
        int maxTokens = contextWindow.getBudget().getMaxTokens();
        int targetTokenCount = (int) (maxTokens * targetRatio);
        List<Message> messages = generateMessagesWithTokens(targetTokenCount);
        contextWindow.replaceMessages(messages);
    }

    private List<Message> generateMessagesWithTokens(int targetTokens) {
        List<Message> messages = new ArrayList<>();
        TokenEstimator estimator = new TokenEstimator();
        messages.add(Message.system("You are a helpful assistant."));

        int tokens = 0;
        int messageId = 0;
        while (tokens < targetTokens) {
            String content = generateLongText(500);
            Message msg = Message.user("Message " + messageId + ": " + content);
            messages.add(msg);
            tokens = estimator.estimateTokens(messages);
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

    private List<Message> createStubCompactionResponse() {
        List<Message> result = new ArrayList<>();
        result.add(Message.system("Compressed summary"));
        result.add(Message.user("Recent message"));
        return result;
    }
}
