package com.example.agent.intent;

import com.example.agent.llm.exception.LlmApiException;
import com.example.agent.llm.exception.LlmConnectionException;
import com.example.agent.llm.exception.LlmTimeoutException;
import com.example.agent.llm.model.Message;
import com.example.agent.testutil.LlmResponseBuilder;
import com.example.agent.testutil.MockLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmIntentRecognizerTest {

    private MockLlmClient mockLlmClient;
    private LlmIntentRecognizer recognizer;

    @BeforeEach
    void setUp() {
        mockLlmClient = new MockLlmClient();
        recognizer = new LlmIntentRecognizer(mockLlmClient);
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("空输入和空白输入应返回UNKNOWN")
        void testNullOrEmptyInput(String input) {
            IntentResult result = recognizer.recognize(input);
            
            assertEquals(IntentType.UNKNOWN, result.getType());
            assertEquals(0.0, result.getConfidence());
        }

        @Test
        @DisplayName("超长输入应正常处理")
        void testVeryLongInput() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("测试输入");
            }
            String longInput = sb.toString();
            
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize(longInput);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("特殊字符输入应正常处理")
        void testSpecialCharacters() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("!@#$%^&*()");
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("JSON解析测试")
    class JsonParsingTests {

        @Test
        @DisplayName("解析标准JSON响应")
        void testParseStandardJson() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"CODE_GENERATION\", \"confidence\": 0.95, \"reasoning\": \"用户请求生成代码\"}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("写一个函数");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.95, result.getConfidence());
            assertEquals("用户请求生成代码", result.getReasoning());
        }

        @Test
        @DisplayName("解析带实体的JSON响应")
        void testParseJsonWithEntities() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"CODE_MODIFICATION\", \"confidence\": 0.9, \"entities\": {\"target_file\": \"Test.java\", \"language\": \"Java\"}, \"reasoning\": \"修改Java文件\"}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("修改Test.java");
            
            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
            assertEquals("Test.java", result.getEntityAsString("target_file"));
            assertEquals("Java", result.getEntityAsString("language"));
        }

        @Test
        @DisplayName("解析JSON前后有额外文本")
        void testParseJsonWithExtraText() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("这是分析结果：{\"intent_type\": \"DEBUGGING\", \"confidence\": 0.85} 结束")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("程序报错了");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("解析缺失字段的JSON")
        void testParseJsonWithMissingFields() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试");
            
            assertEquals(IntentType.UNKNOWN, result.getType());
            assertEquals(0.5, result.getConfidence());
        }

        @Test
        @DisplayName("解析无效JSON回退到规则识别器")
        void testParseInvalidJson() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("这不是JSON")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("解析小写意图类型")
        void testParseLowercaseIntentType() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"code_generation\", \"confidence\": 0.9}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("解析中文显示名称意图类型")
        void testParseChineseIntentType() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"代码生成\", \"confidence\": 0.9}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("解析未知意图类型")
        void testParseUnknownIntentType() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"UNKNOWN_TYPE\", \"confidence\": 0.9}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试");
            
            assertEquals(IntentType.UNKNOWN, result.getType());
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("LLM API异常时回退到规则识别器")
        void testLlmApiException() {
            mockLlmClient.setExceptionToThrow(new LlmApiException("API错误", 500, null));
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("连接异常时回退到规则识别器")
        void testConnectionException() {
            mockLlmClient.setExceptionToThrow(new LlmConnectionException("连接失败", "http://test.com", null));
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("超时异常时回退到规则识别器")
        void testTimeoutException() {
            mockLlmClient.setExceptionToThrow(new LlmTimeoutException("请求超时", 30, null));
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("空响应时回退到规则识别器")
        void testEmptyResponse() {
            mockLlmClient.enqueueResponse(LlmResponseBuilder.empty());
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("null响应时回退到规则识别器")
        void testNullResponse() {
            mockLlmClient.enqueueResponse(null);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("禁用状态测试")
    class DisabledTests {

        @Test
        @DisplayName("禁用时使用规则识别器")
        void testDisabled() {
            recognizer.setEnabled(false);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
            assertTrue(mockLlmClient.getRecordedMessages().isEmpty());
        }

        @Test
        @DisplayName("启用状态")
        void testEnabled() {
            assertTrue(recognizer.isEnabled());
            
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8}")
                    .build()
            );
            
            recognizer.recognize("测试");
            
            assertFalse(mockLlmClient.getRecordedMessages().isEmpty());
        }

        @Test
        @DisplayName("动态切换启用状态")
        void testToggleEnabled() {
            assertTrue(recognizer.isEnabled());
            
            recognizer.setEnabled(false);
            assertFalse(recognizer.isEnabled());
            
            recognizer.setEnabled(true);
            assertTrue(recognizer.isEnabled());
        }
    }

    @Nested
    @DisplayName("上下文参数测试")
    class ContextTests {

        @Test
        @DisplayName("带上下文的识别")
        void testWithContext() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8}")
                    .build()
            );
            
            List<Message> context = new ArrayList<>();
            context.add(Message.user("之前的问题"));
            
            IntentResult result = recognizer.recognize("继续", context);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("空上下文列表")
        void testEmptyContext() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试", new ArrayList<>());
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("null上下文")
        void testNullContext() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试", null);
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("接口方法测试")
    class InterfaceTests {

        @Test
        @DisplayName("isEnabled返回正确状态")
        void testIsEnabled() {
            assertTrue(recognizer.isEnabled());
            
            recognizer.setEnabled(false);
            assertFalse(recognizer.isEnabled());
        }

        @Test
        @DisplayName("getName返回类名")
        void testGetName() {
            assertEquals("LlmIntentRecognizer", recognizer.getName());
        }
    }

    @Nested
    @DisplayName("置信度边界测试")
    class ConfidenceBoundaryTests {

        @Test
        @DisplayName("置信度为0")
        void testZeroConfidence() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.0}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试");
            
            assertEquals(0.0, result.getConfidence());
        }

        @Test
        @DisplayName("置信度为1")
        void testMaxConfidence() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 1.0}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试");
            
            assertEquals(1.0, result.getConfidence());
        }

        @Test
        @DisplayName("置信度超出范围")
        void testConfidenceOutOfRange() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 1.5}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试");
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("置信度为负数")
        void testNegativeConfidence() {
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": -0.5}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("测试");
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("所有意图类型测试")
    class AllIntentTypesTests {

        @Test
        @DisplayName("识别所有意图类型")
        void testAllIntentTypes() {
            for (IntentType type : IntentType.values()) {
                mockLlmClient.reset();
                mockLlmClient.enqueueResponse(
                    LlmResponseBuilder.create()
                        .content(String.format("{\"intent_type\": \"%s\", \"confidence\": 0.9}", type.name()))
                        .build()
                );
                
                IntentResult result = recognizer.recognize("测试");
                
                assertEquals(type, result.getType(), "Failed for type: " + type);
            }
        }
    }
}
