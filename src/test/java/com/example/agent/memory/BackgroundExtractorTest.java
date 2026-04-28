package com.example.agent.memory;

import com.example.agent.context.SessionCompactionState;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BackgroundExtractorTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private TokenEstimator tokenEstimator;

    @TempDir
    Path tempDir;

    private BackgroundExtractor extractor;
    private String testSessionId;
    private SessionCompactionState compactionState;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSessionId = "test-session-" + System.currentTimeMillis();
        compactionState = new SessionCompactionState();
        extractor = new BackgroundExtractor(testSessionId, tokenEstimator, llmClient, compactionState, tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
    }

    @Test
    void testShouldNotExtractBelowInitialThreshold() throws Exception {
        Method shouldExtract = BackgroundExtractor.class.getDeclaredMethod(
            "shouldExtract", List.class);
        shouldExtract.setAccessible(true);

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(Message.user("test"));
            messages.add(Message.assistant("response"));
        }

        assertFalse((Boolean) shouldExtract.invoke(extractor, messages),
            "消息数少于阈值不应触发提取");
    }

    @Test
    void testConcurrentExtractionRequestsDoNotOverlap() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);
        lock.set(true);

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            messages.add(Message.user("test"));
            messages.add(Message.assistant("response"));
        }

        assertDoesNotThrow(() -> extractor.requestExtractionAfterCompaction(messages),
            "提取进行中时，后续请求应静默跳过");

        lock.set(false);
    }
}
