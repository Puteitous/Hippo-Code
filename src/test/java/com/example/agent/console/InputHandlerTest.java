package com.example.agent.console;

import com.example.agent.service.TokenEstimator;
import org.jline.reader.LineReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InputHandlerTest {

    private LineReader reader;
    private TokenEstimator tokenEstimator;
    private InputHandler inputHandler;

    @BeforeEach
    void setUp() {
        reader = mock(LineReader.class);
        tokenEstimator = new TokenEstimator();
        inputHandler = new InputHandler(reader, tokenEstimator);
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("正常构造")
        void testNormalConstructor() {
            InputHandler handler = new InputHandler(reader, tokenEstimator);
            
            assertNotNull(handler);
        }

        @Test
        @DisplayName("null reader抛出异常")
        void testNullReader() {
            assertThrows(NullPointerException.class, () -> {
                new InputHandler(null, tokenEstimator);
            });
        }

        @Test
        @DisplayName("null tokenEstimator抛出异常")
        void testNullTokenEstimator() {
            assertThrows(NullPointerException.class, () -> {
                new InputHandler(reader, null);
            });
        }
    }

    @Nested
    @DisplayName("truncate方法测试")
    class TruncateTests {

        @Test
        @DisplayName("null文本返回空字符串")
        void testNullText() {
            assertEquals("", inputHandler.truncate(null, 10));
        }

        @Test
        @DisplayName("空文本返回空字符串")
        void testEmptyText() {
            assertEquals("", inputHandler.truncate("", 10));
        }

        @Test
        @DisplayName("maxLength为零返回空字符串")
        void testZeroMaxLength() {
            assertEquals("", inputHandler.truncate("test", 0));
        }

        @Test
        @DisplayName("maxLength为负数返回空字符串")
        void testNegativeMaxLength() {
            assertEquals("", inputHandler.truncate("test", -5));
        }

        @Test
        @DisplayName("文本短于maxLength不截断")
        void testTextShorterThanMaxLength() {
            assertEquals("test", inputHandler.truncate("test", 10));
        }

        @Test
        @DisplayName("文本等于maxLength不截断")
        void testTextEqualsMaxLength() {
            assertEquals("test", inputHandler.truncate("test", 4));
        }

        @Test
        @DisplayName("文本长于maxLength截断并添加省略号")
        void testTextLongerThanMaxLength() {
            String result = inputHandler.truncate("hello world", 5);
            
            assertEquals("hello...", result);
        }

        @Test
        @DisplayName("超长文本截断")
        void testVeryLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("a");
            }
            
            String result = inputHandler.truncate(sb.toString(), 100);
            
            assertEquals(103, result.length());
            assertTrue(result.endsWith("..."));
        }
    }

    @Nested
    @DisplayName("handleLongInput方法测试")
    class HandleLongInputTests {

        @Test
        @DisplayName("null输入返回null")
        void testNullInput() {
            String result = inputHandler.handleLongInput(null, 100);
            
            assertNull(result);
        }

        @Test
        @DisplayName("空输入返回null")
        void testEmptyInput() {
            String result = inputHandler.handleLongInput("", 100);
            
            assertNull(result);
        }

        @Test
        @DisplayName("tokens为零返回原输入")
        void testZeroTokens() {
            String result = inputHandler.handleLongInput("test", 0);
            
            assertEquals("test", result);
        }

        @Test
        @DisplayName("tokens为负数返回原输入")
        void testNegativeTokens() {
            String result = inputHandler.handleLongInput("test", -100);
            
            assertEquals("test", result);
        }
    }

    @Nested
    @DisplayName("getMaxInputTokens方法测试")
    class GetMaxInputTokensTests {

        @Test
        @DisplayName("返回最大输入Token限制")
        void testGetMaxInputTokens() {
            assertEquals(10000, inputHandler.getMaxInputTokens());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("truncate边界值-maxLength为1")
        void testTruncateMaxLengthOne() {
            assertEquals("a...", inputHandler.truncate("abc", 1));
        }

        @Test
        @DisplayName("truncate边界值-单字符文本")
        void testTruncateSingleChar() {
            assertEquals("a", inputHandler.truncate("a", 10));
        }

        @Test
        @DisplayName("handleLongInput边界值-刚好超限")
        void testHandleLongInputJustOverLimit() {
            when(reader.readLine(anyString())).thenReturn("");
            
            String longInput = "a".repeat(25000);
            String result = inputHandler.handleLongInput(longInput, 10001);
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("特殊字符测试")
    class SpecialCharacterTests {

        @Test
        @DisplayName("truncate包含换行符的文本")
        void testTruncateWithNewlines() {
            String text = "line1\nline2\nline3";
            
            String result = inputHandler.truncate(text, 5);
            
            assertEquals("line1...", result);
        }

        @Test
        @DisplayName("truncate包含Unicode字符的文本")
        void testTruncateWithUnicode() {
            String text = "你好世界测试";
            
            String result = inputHandler.truncate(text, 3);
            
            assertEquals("你好世...", result);
        }

        @Test
        @DisplayName("truncate包含制表符的文本")
        void testTruncateWithTabs() {
            String text = "col1\tcol2\tcol3";
            
            String result = inputHandler.truncate(text, 5);
            
            assertEquals("col1\t...", result);
        }
    }
}
