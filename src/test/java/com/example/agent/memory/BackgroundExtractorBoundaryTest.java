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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class BackgroundExtractorBoundaryTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private SessionCompactionState compactionState;

    @TempDir
    Path tempDir;

    private BackgroundExtractor extractor;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSessionId = "test-session-" + System.currentTimeMillis();
        extractor = new BackgroundExtractor(testSessionId, tokenEstimator, llmClient, compactionState, tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
    }

    @Test
    void testCheckAndExtractWithNullDoesNothing() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        assertDoesNotThrow(() -> extractor.checkAndExtract(null),
            "checkAndExtract(null) 不应抛出异常");
        assertFalse(lock.get(), "null 输入不应获取锁");
    }

    @Test
    void testCheckAndExtractWithEmptyListDoesNothing() throws Exception {
        Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
        lockField.setAccessible(true);
        AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);

        assertDoesNotThrow(() -> extractor.checkAndExtract(Collections.emptyList()),
            "checkAndExtract(empty) 不应抛出异常");
        assertFalse(lock.get(), "空列表不应获取锁");
    }
}
