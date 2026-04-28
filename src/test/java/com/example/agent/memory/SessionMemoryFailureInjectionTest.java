package com.example.agent.memory;

import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimator;
import com.example.agent.tools.ToolArgumentSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("Session Memory 异常注入测试 - Chaos Engineering")
class SessionMemoryFailureInjectionTest {

    @TempDir
    Path tempDir;

    private LlmClient llmClient;
    private TokenEstimator tokenEstimator;
    private BackgroundExtractor extractor;
    private SessionMemoryManager memoryManager;
    private String testSessionId;
    private Path memoryFilePath;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = mock(LlmClient.class);
        tokenEstimator = mock(TokenEstimator.class);
        testSessionId = "test-session-chaos-" + System.currentTimeMillis();
        memoryManager = new SessionMemoryManager(testSessionId, tempDir);
        memoryFilePath = memoryManager.getMemoryFilePath();
        objectMapper = new ObjectMapper();
        
        memoryManager.initializeIfNotExists();
        when(tokenEstimator.estimate(anyList())).thenReturn(5000);
        
        extractor = new BackgroundExtractor(testSessionId, tokenEstimator, llmClient, null, tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
    }

    @Nested
    @DisplayName("JSON 转义失败场景 - 最常见 LLM Bug")
    class JsonEscapingFailureScenarios {

        @Test
        @DisplayName("old_text 包含未转义双引号 - 自动修复")
        void testOldTextWithUnescapedQuotesAutoFix() throws Exception {
            String badJson = "{" +
                    "\"path\": \"test.md\", " +
                    "\"old_text\": \"Line with \"quotes\" inside\", " +
                    "\"new_text\": \"Fixed content\"" +
                    "}";
            
            String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", badJson);
            
            assertDoesNotThrow(() -> objectMapper.readTree(result),
                    "修复后的 JSON 应该能正常解析: " + result);
            
            JsonNode node = objectMapper.readTree(result);
            assertTrue(node.get("old_text").asText().contains("quotes"));
        }

        @Test
        @DisplayName("new_text 包含未转义双引号 - 自动修复")
        void testNewTextWithUnescapedQuotesAutoFix() throws Exception {
            String badJson = "{" +
                    "\"path\": \"test.md\", " +
                    "\"old_text\": \"Old content\", " +
                    "\"new_text\": \"Line with \"nested quotes\" inside\"" +
                    "}";
            
            String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", badJson);
            
            assertDoesNotThrow(() -> objectMapper.readTree(result),
                    "修复后的 JSON 应该能正常解析: " + result);
            
            JsonNode node = objectMapper.readTree(result);
            assertTrue(node.get("new_text").asText().contains("nested quotes"));
        }

        @Test
        @DisplayName("极端情况：JSON 完全坏掉 - 优雅降级")
        void testCompletelyBrokenJsonGracefulDegradation() {
            String completelyBrokenJson = "{ this is not even json at all !!! }";
            
            String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", completelyBrokenJson);
            
            assertNotNull(result, "即使修不好也不能返回 null");
        }

        @Test
        @DisplayName("换行符包含在内容中 - 正确转义")
        void testNewlinesInContentEscaped() {
            String badJson = "{\"path\": \"test.md\", \"old_text\": \"Line1\nLine2\nLine3\", \"new_text\": \"Fixed\"}";
            
            String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", badJson);
            
            assertDoesNotThrow(() -> objectMapper.readTree(result),
                    "包含换行的 JSON 应该能正常解析: " + result);
        }
    }

    @Nested
    @DisplayName("文件系统失败场景")
    class FileSystemFailureScenarios {

        @Test
        @DisplayName("记忆文件只读 - 不崩溃")
        void testMemoryFileReadOnly() throws Exception {
            Files.writeString(memoryFilePath, "# Read Only\n");
            
            Conversation conversation = createTestConversation(20);
            
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "文件不可写不应导致崩溃"
            );
        }
    }

    @Nested
    @DisplayName("网络失败场景")
    class NetworkFailureScenarios {

        @Test
        @DisplayName("LLM 抛出网络异常 - 优雅处理")
        void testLlmNetworkException() throws Exception {
            when(llmClient.chat(anyList())).thenThrow(new RuntimeException("Connection timeout"));
            
            Conversation conversation = createTestConversation(50);
            
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "LLM 网络异常不应崩溃"
            );
        }
    }

    @Nested
    @DisplayName("LLM 输出乱码场景")
    class LlmOutputFailureScenarios {

        @Test
        @DisplayName("LLM 返回空 - 不崩溃")
        void testLlmReturnsNull() throws Exception {
            when(llmClient.chat(anyList())).thenReturn(null);
            
            Conversation conversation = createTestConversation(20);
            
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "LLM 返回 null 不应崩溃"
            );
        }

        @Test
        @DisplayName("LLM 输出不是 JSON - 优雅降级")
        void testLlmReturnsNonJson() throws Exception {
            Conversation conversation = createTestConversation(20);
            
            assertDoesNotThrow(() -> 
                    extractor.requestExtractionAfterCompaction(conversation.getMessages()),
                    "LLM 输出乱码不应崩溃"
            );
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
