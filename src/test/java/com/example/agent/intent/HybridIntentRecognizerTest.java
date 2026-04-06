package com.example.agent.intent;

import com.example.agent.config.IntentConfig;
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

class HybridIntentRecognizerTest {

    private MockLlmClient mockLlmClient;
    private HybridIntentRecognizer recognizer;
    private IntentConfig.RecognitionStrategy config;

    @BeforeEach
    void setUp() {
        mockLlmClient = new MockLlmClient();
        config = new IntentConfig.RecognitionStrategy();
        config.setLlmEnabled(true);
        config.setPreferLlm(false);
        config.setHighConfidenceThreshold(0.85);
        config.setLowConfidenceThreshold(0.50);
        recognizer = new HybridIntentRecognizer(mockLlmClient, config);
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
                sb.append("写代码");
            }
            String longInput = sb.toString();
            
            IntentResult result = recognizer.recognize(longInput);
            
            assertNotNull(result);
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("特殊字符输入应正常处理")
        void testSpecialCharacters() {
            IntentResult result = recognizer.recognize("!@#$%^&*()写代码");
            
            assertNotNull(result);
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }
    }

    @Nested
    @DisplayName("高置信度规则识别测试")
    class HighConfidenceRuleTests {

        @Test
        @DisplayName("规则识别器高置信度时直接返回规则结果")
        void testHighConfidenceRuleResult() {
            config.setHighConfidenceThreshold(0.85);
            config.setPreferLlm(false);
            
            IntentResult result = recognizer.recognize("写一个排序算法的代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
            assertEquals("匹配代码生成模式", result.getReasoning());
            assertTrue(mockLlmClient.getRecordedMessages().isEmpty());
        }

        @Test
        @DisplayName("置信度等于阈值时返回规则结果")
        void testConfidenceEqualToThreshold() {
            config.setHighConfidenceThreshold(0.85);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("置信度略高于阈值时返回规则结果")
        void testConfidenceSlightlyAboveThreshold() {
            config.setHighConfidenceThreshold(0.84);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertTrue(mockLlmClient.getRecordedMessages().isEmpty());
        }
    }

    @Nested
    @DisplayName("LLM识别器调用测试")
    class LlmRecognizerTests {

        @Test
        @DisplayName("低置信度时调用LLM识别器")
        void testLowConfidenceCallsLlm() {
            config.setLowConfidenceThreshold(0.60);
            config.setHighConfidenceThreshold(0.90);
            config.setPreferLlm(false);
            
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.9, \"reasoning\": \"LLM分析\"}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("随便说说");
            
            assertFalse(mockLlmClient.getRecordedMessages().isEmpty());
        }

        @Test
        @DisplayName("preferLlm=true时优先使用LLM")
        void testPreferLlmTrue() {
            config.setPreferLlm(true);
            
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"CODE_GENERATION\", \"confidence\": 0.95, \"reasoning\": \"LLM分析\"}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertFalse(mockLlmClient.getRecordedMessages().isEmpty());
        }

        @Test
        @DisplayName("preferLlm=false时高置信度规则优先")
        void testPreferLlmFalse() {
            config.setPreferLlm(false);
            config.setHighConfidenceThreshold(0.85);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertTrue(mockLlmClient.getRecordedMessages().isEmpty());
            assertEquals("匹配代码生成模式", result.getReasoning());
        }

        @Test
        @DisplayName("LLM返回更高置信度时使用LLM结果")
        void testLlmHigherConfidence() {
            config.setLowConfidenceThreshold(0.40);
            config.setHighConfidenceThreshold(0.90);
            config.setPreferLlm(false);
            
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"DEBUGGING\", \"confidence\": 0.95, \"reasoning\": \"LLM检测到调试需求\"}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("随便说说");
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("LLM禁用测试")
    class LlmDisabledTests {

        @Test
        @DisplayName("LLM禁用时只使用规则识别器")
        void testLlmDisabled() {
            config.setLlmEnabled(false);
            recognizer.setLlmEnabled(false);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
            assertTrue(mockLlmClient.getRecordedMessages().isEmpty());
        }

        @Test
        @DisplayName("通过setLlmEnabled禁用LLM")
        void testSetLlmEnabledFalse() {
            recognizer.setLlmEnabled(false);
            
            IntentResult result = recognizer.recognize("写代码");
            
            assertTrue(mockLlmClient.getRecordedMessages().isEmpty());
        }
    }

    @Nested
    @DisplayName("上下文参数测试")
    class ContextTests {

        @Test
        @DisplayName("带上下文的识别")
        void testWithContext() {
            List<Message> context = new ArrayList<>();
            context.add(Message.user("之前的问题"));
            context.add(Message.assistant("之前的回答"));
            
            IntentResult result = recognizer.recognize("写代码", context);
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("空上下文列表")
        void testEmptyContext() {
            IntentResult result = recognizer.recognize("写代码", new ArrayList<>());
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("null上下文")
        void testNullContext() {
            IntentResult result = recognizer.recognize("写代码", null);
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }
    }

    @Nested
    @DisplayName("配置测试")
    class ConfigurationTests {

        @Test
        @DisplayName("setPreferLlm修改配置")
        void testSetPreferLlm() {
            assertFalse(recognizer.isPreferLlm());
            
            recognizer.setPreferLlm(true);
            assertTrue(recognizer.isPreferLlm());
            
            recognizer.setPreferLlm(false);
            assertFalse(recognizer.isPreferLlm());
        }

        @Test
        @DisplayName("setLlmEnabled修改配置")
        void testSetLlmEnabled() {
            assertTrue(recognizer.isEnabled());
            
            recognizer.setLlmEnabled(false);
            assertTrue(recognizer.isEnabled());
            
            recognizer.setLlmEnabled(true);
            assertTrue(recognizer.isEnabled());
        }

        @Test
        @DisplayName("不同阈值配置")
        void testDifferentThresholds() {
            IntentConfig.RecognitionStrategy customConfig = new IntentConfig.RecognitionStrategy();
            customConfig.setHighConfidenceThreshold(0.95);
            customConfig.setLowConfidenceThreshold(0.30);
            customConfig.setLlmEnabled(false);
            
            HybridIntentRecognizer customRecognizer = new HybridIntentRecognizer(mockLlmClient, customConfig);
            
            IntentResult result = customRecognizer.recognize("写代码");
            
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
            
            recognizer.setLlmEnabled(false);
            assertTrue(recognizer.isEnabled());
        }

        @Test
        @DisplayName("getName返回类名")
        void testGetName() {
            assertEquals("HybridIntentRecognizer", recognizer.getName());
        }
    }

    @Nested
    @DisplayName("置信度边界测试")
    class ConfidenceBoundaryTests {

        @Test
        @DisplayName("置信度刚好低于低阈值")
        void testConfidenceJustBelowLowThreshold() {
            config.setLowConfidenceThreshold(0.55);
            config.setHighConfidenceThreshold(0.90);
            config.setPreferLlm(false);
            
            mockLlmClient.enqueueResponse(
                LlmResponseBuilder.create()
                    .content("{\"intent_type\": \"QUESTION\", \"confidence\": 0.8, \"reasoning\": \"LLM分析\"}")
                    .build()
            );
            
            IntentResult result = recognizer.recognize("随便说说");
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("置信度在高阈值和低阈值之间")
        void testConfidenceBetweenThresholds() {
            config.setLowConfidenceThreshold(0.40);
            config.setHighConfidenceThreshold(0.90);
            config.setPreferLlm(false);
            
            IntentResult result = recognizer.recognize("这个函数");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.60, result.getConfidence());
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("使用默认配置构造")
        void testDefaultConstructor() {
            HybridIntentRecognizer defaultRecognizer = new HybridIntentRecognizer(mockLlmClient);
            
            assertNotNull(defaultRecognizer);
            assertTrue(defaultRecognizer.isEnabled());
        }

        @Test
        @DisplayName("使用自定义配置构造")
        void testCustomConfigConstructor() {
            IntentConfig.RecognitionStrategy customConfig = new IntentConfig.RecognitionStrategy();
            customConfig.setLlmEnabled(false);
            customConfig.setPreferLlm(true);
            
            HybridIntentRecognizer customRecognizer = new HybridIntentRecognizer(mockLlmClient, customConfig);
            
            assertNotNull(customRecognizer);
        }
    }

    @Nested
    @DisplayName("isPreferLlm方法测试")
    class IsPreferLlmTests {

        @Test
        @DisplayName("默认preferLlm为false")
        void testDefaultPreferLlm() {
            assertFalse(recognizer.isPreferLlm());
        }

        @Test
        @DisplayName("通过配置设置preferLlm")
        void testConfigPreferLlm() {
            config.setPreferLlm(true);
            HybridIntentRecognizer newRecognizer = new HybridIntentRecognizer(mockLlmClient, config);
            
            assertTrue(newRecognizer.isPreferLlm());
        }
    }
}
