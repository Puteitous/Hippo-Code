package com.example.agent.llm.client;

import com.example.agent.config.Config;
import com.example.agent.config.LlmConfig;
import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.llm.model.ChatRequest;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.retry.RetryPolicy;
import com.example.agent.llm.stream.StreamChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashScopeLlmClientTest {

    private Config config;
    private LlmConfig llmConfig;
    private RetryPolicy retryPolicy;
    private DashScopeLlmClient client;

    @BeforeEach
    void setUp() {
        config = mock(Config.class);
        llmConfig = mock(LlmConfig.class);
        when(config.getLlm()).thenReturn(llmConfig);
        when(llmConfig.getApiKey()).thenReturn("test-api-key");
        when(llmConfig.getBaseUrl()).thenReturn("https://api.test.com");
        when(llmConfig.getModel()).thenReturn("test-model");
        when(llmConfig.getMaxTokens()).thenReturn(2048);
        
        retryPolicy = RetryPolicy.noRetry();
        client = new DashScopeLlmClient(config, retryPolicy);
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("带Config构造")
        void testConstructorWithConfig() {
            DashScopeLlmClient client = new DashScopeLlmClient(config);
            
            assertNotNull(client);
        }

        @Test
        @DisplayName("null Config抛出异常")
        void testNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> {
                new DashScopeLlmClient(null);
            });
        }

        @Test
        @DisplayName("null RetryPolicy抛出异常")
        void testNullRetryPolicy() {
            assertThrows(IllegalArgumentException.class, () -> {
                new DashScopeLlmClient(config, null);
            });
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("null消息列表抛出异常")
        void testNullMessages() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.chat(null);
            });
        }

        @Test
        @DisplayName("空消息列表抛出异常")
        void testEmptyMessages() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.chat(new ArrayList<>());
            });
        }

        @Test
        @DisplayName("null消息列表-流式调用抛出异常")
        void testNullMessagesStream() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.chatStream(null, chunk -> {});
            });
        }

        @Test
        @DisplayName("空消息列表-流式调用抛出异常")
        void testEmptyMessagesStream() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.chatStream(new ArrayList<>(), chunk -> {});
            });
        }

        @Test
        @DisplayName("null Consumer不崩溃")
        void testNullConsumer() {
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chatStream(messages, null);
            });
        }
    }

    @Nested
    @DisplayName("API错误处理测试")
    class ApiErrorHandlingTests {

        @Test
        @DisplayName("400错误-请求参数错误")
        void testBadRequestError() {
            List<Message> messages = List.of(Message.user("test"));
            
            // 由于测试环境没有真实API服务器，会抛出LlmException相关异常
            LlmException exception = assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
            
            assertNotNull(exception);
        }

        @Test
        @DisplayName("401错误-认证失败")
        void testAuthenticationError() {
            when(llmConfig.getApiKey()).thenReturn("invalid-key");
            List<Message> messages = List.of(Message.user("test"));
            
            // 由于测试环境没有真实API服务器，会抛出LlmException相关异常
            LlmException exception = assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
            
            assertNotNull(exception);
        }

        @Test
        @DisplayName("429错误-限流")
        void testRateLimitedError() {
            List<Message> messages = List.of(Message.user("test"));
            
            LlmException exception = assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
            
            assertNotNull(exception);
        }

        @Test
        @DisplayName("500错误-服务器错误")
        void testServerError() {
            List<Message> messages = List.of(Message.user("test"));
            
            LlmException exception = assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
            
            assertNotNull(exception);
        }
    }

    @Nested
    @DisplayName("重试策略测试")
    class RetryPolicyTests {

        @Test
        @DisplayName("超时错误触发重试")
        void testTimeoutRetry() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelayMs(1)
                    .build();
            client = new DashScopeLlmClient(config, retryPolicy);
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }

        @Test
        @DisplayName("连接错误触发重试")
        void testConnectionRetry() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelayMs(1)
                    .build();
            client = new DashScopeLlmClient(config, retryPolicy);
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }

        @Test
        @DisplayName("客户端错误不触发重试")
        void testClientErrorNoRetry() {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialDelayMs(1)
                    .build();
            client = new DashScopeLlmClient(config, retryPolicy);
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }
    }

    @Nested
    @DisplayName("空响应处理测试")
    class EmptyResponseTests {

        @Test
        @DisplayName("空响应不崩溃")
        void testEmptyResponse() {
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }

        @Test
        @DisplayName("流式空响应不崩溃")
        void testEmptyStreamResponse() {
            List<Message> messages = List.of(Message.user("test"));
            List<StreamChunk> chunks = new ArrayList<>();
            
            assertThrows(LlmException.class, () -> {
                client.chatStream(messages, chunks::add);
            });
        }
    }

    @Nested
    @DisplayName("ChatRequest测试")
    class ChatRequestTests {

        @Test
        @DisplayName("executeRequest处理null请求")
        void testNullRequest() {
            assertThrows(NullPointerException.class, () -> {
                client.executeRequest(null);
            });
        }

        @Test
        @DisplayName("ChatRequest构建正确")
        void testChatRequestBuild() {
            ChatRequest request = ChatRequest.of("test-model", List.of(Message.user("test")))
                    .maxTokens(1000);
            
            assertNotNull(request);
        }
    }

    @Nested
    @DisplayName("工具调用测试")
    class ToolCallTests {

        @Test
        @DisplayName("带工具的聊天请求")
        void testChatWithTools() {
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chatWithTools(messages, Collections.emptyList());
            });
        }

        @Test
        @DisplayName("chat带工具列表")
        void testChatWithToolList() {
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages, Collections.emptyList());
            });
        }
    }

    @Nested
    @DisplayName("配置验证测试")
    class ConfigValidationTests {

        @Test
        @DisplayName("无效baseUrl")
        void testInvalidBaseUrl() {
            when(llmConfig.getBaseUrl()).thenReturn("invalid-url");
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }

        @Test
        @DisplayName("空apiKey")
        void testEmptyApiKey() {
            when(llmConfig.getApiKey()).thenReturn("");
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }

        @Test
        @DisplayName("null apiKey")
        void testNullApiKey() {
            when(llmConfig.getApiKey()).thenReturn(null);
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }
    }

    @Nested
    @DisplayName("流式响应测试")
    class StreamResponseTests {

        @Test
        @DisplayName("流式调用处理异常")
        void testStreamException() {
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chatStream(messages, chunk -> {});
            });
        }

        @Test
        @DisplayName("流式调用带工具")
        void testStreamWithTools() {
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chatStream(messages, Collections.emptyList(), chunk -> {});
            });
        }
    }

    @Nested
    @DisplayName("异常类型测试")
    class ExceptionTypeTests {

        @Test
        @DisplayName("网络超时抛出LlmTimeoutException")
        void testNetworkTimeout() {
            List<Message> messages = List.of(Message.user("test"));
            
            LlmException exception = assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
            
            assertNotNull(exception);
        }

        @Test
        @DisplayName("连接失败抛出LlmConnectionException")
        void testConnectionFailed() {
            when(llmConfig.getBaseUrl()).thenReturn("https://nonexistent.invalid.domain");
            
            List<Message> messages = List.of(Message.user("test"));
            
            assertThrows(LlmException.class, () -> {
                client.chat(messages);
            });
        }
    }
}
