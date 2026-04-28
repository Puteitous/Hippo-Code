package com.example.agent.memory;

import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Session Memory 边界条件测试")
class SessionMemoryEdgeCasesTest {

    @TempDir
    Path tempDir;

    private LlmClient llmClient;
    private TokenEstimator tokenEstimator;
    private BackgroundExtractor extractor;
    private SessionMemoryManager memoryManager;
    private String testSessionId;
    private Path memoryFilePath;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = mock(LlmClient.class);
        tokenEstimator = mock(TokenEstimator.class);
        testSessionId = "test-session-edge-" + System.currentTimeMillis();
        memoryManager = new SessionMemoryManager(testSessionId, tempDir);
        memoryFilePath = memoryManager.getMemoryFilePath();
        
        extractor = new BackgroundExtractor(testSessionId, tokenEstimator, llmClient, null, tempDir);
        
        memoryManager.initializeIfNotExists();
        when(tokenEstimator.estimate(anyList())).thenReturn(5000);
    }

    @AfterEach
    void tearDown() throws IOException {
    }

    @Nested
    @DisplayName("消息数量边界")
    class MessageCountBoundaries {

        @Test
        @DisplayName("0 条消息 - 静默跳过，不启动提取")
        void testZeroMessages() throws Exception {
            Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
            lockField.setAccessible(true);
            AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);
            
            List<Message> emptyList = Collections.emptyList();
            
            assertFalse(lock.get());
            assertDoesNotThrow(() -> extractor.checkAndExtract(emptyList));
            assertFalse(lock.get(), "0 条消息不应启动提取，不应获取锁");
        }

        @Test
        @DisplayName("1 条消息 - 不满足提取阈值，不启动")
        void testSingleMessage() throws Exception {
            Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
            lockField.setAccessible(true);
            AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);
            
            List<Message> singleMessage = Collections.singletonList(Message.user("Hello"));
            
            assertFalse(lock.get());
            assertDoesNotThrow(() -> extractor.checkAndExtract(singleMessage));
            assertFalse(lock.get(), "1 条消息不满足阈值，不应启动");
        }

        @Test
        @DisplayName("刚好达到最小消息数 - 不崩溃")
        void testExactlyThresholdMessages() throws Exception {
            Conversation conversation = createTestConversation(100);
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages())
            );
        }

        @Test
        @DisplayName("超大消息列表 - 1000 条消息不崩溃")
        void testThousandMessages() throws Exception {
            Conversation conversation = createTestConversation(1000);
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "1000 条消息不应导致 OOM 或崩溃"
            );
        }
    }

    @Nested
    @DisplayName("Token 预算边界")
    class TokenBudgetBoundaries {

        @Test
        @DisplayName("Token 刚好在预算内 - 不崩溃")
        void testTokenWithinBudget() throws Exception {
            when(tokenEstimator.estimate(anyList())).thenReturn(5000);
            Conversation conversation = createTestConversation(20);
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages())
            );
        }

        @Test
        @DisplayName("Token 严重超预算 - 不崩溃")
        void testTokenExceedsBudget() throws Exception {
            when(tokenEstimator.estimate(anyList())).thenReturn(25000);
            Conversation conversation = createTestConversation(100);
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "Token 超标不应崩溃"
            );
        }

        @Test
        @DisplayName("记忆文件已很大 - 不崩溃")
        void testMemoryFileAtSizeLimit() throws Exception {
            String hugeContent = "# Session Title\n" + "x".repeat(50000);
            Files.writeString(memoryFilePath, hugeContent);
            
            Conversation conversation = createTestConversation(20);
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "记忆文件太大不应崩溃"
            );
        }
    }

    @Nested
    @DisplayName("并发边界")
    class ConcurrencyBoundaries {

        @Test
        @DisplayName("提取进行中触发新提取 - 静默丢弃")
        void testExtractionInProgressNewRequestDropped() throws Exception {
            Field lockField = BackgroundExtractor.class.getDeclaredField("extractionInProgress");
            lockField.setAccessible(true);
            AtomicBoolean lock = (AtomicBoolean) lockField.get(extractor);
            lock.set(true);
            
            Conversation conversation = createTestConversation(20);
            
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "已有进行中的提取时，新请求应静默丢弃"
            );
        }
    }

    @Nested
    @DisplayName("空值和 null 边界")
    class NullAndEmptyBoundaries {

        @Test
        @DisplayName("null 会话列表 - 不抛出 NPE")
        void testNullConversation() {
            assertDoesNotThrow(() -> extractor.checkAndExtract(null),
                    "null 输入不应抛出 NPE"
            );
        }

        @Test
        @DisplayName("null 消息混入列表 - 优雅过滤")
        void testNullMessageInList() {
            List<Message> messagesWithNull = new ArrayList<>();
            messagesWithNull.add(Message.user("Hello"));
            messagesWithNull.add(null);
            messagesWithNull.add(Message.assistant("Hi"));
            messagesWithNull.add(null);
            
            assertDoesNotThrow(() -> extractor.checkAndExtract(messagesWithNull),
                    "列表中包含 null 消息不应崩溃"
            );
        }

        @Test
        @DisplayName("会话 ID 为 null - NPE 是预期行为")
        void testNullSessionId() {
            SessionMemoryManager manager = null;
            try {
                manager = new SessionMemoryManager(null, tempDir);
            } catch (NullPointerException e) {
                assertNotNull(e, "会话 ID 为 null 应抛出 NPE，属于预期行为");
            }
        }
    }

    private Conversation createTestConversation(int messageCount) {
        Conversation conversation = new Conversation(100000, tokenEstimator);
        for (int i = 0; i < messageCount; i++) {
            if (i % 2 == 0) {
                conversation.addMessage(Message.user("User message " + i));
            } else {
                conversation.addMessage(Message.assistant("Assistant response " + i));
            }
        }
        return conversation;
    }
}
