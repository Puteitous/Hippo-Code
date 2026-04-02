package com.example.agent;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple Java Agent 测试类
 * 
 * 包含基本的单元测试示例，展示常用测试方法
 */
@DisplayName("Simple Java Agent 测试")
class SimpleJavaAgentTest {

    // ==================== 生命周期方法 ====================
    
    @BeforeAll
    static void beforeAll() {
        System.out.println("=== 测试套件开始 ===");
    }

    @AfterAll
    static void afterAll() {
        System.out.println("=== 测试套件结束 ===");
    }

    @BeforeEach
    void beforeEach() {
        System.out.println(">> 测试方法执行前");
    }

    @AfterEach
    void afterEach() {
        System.out.println("<< 测试方法执行后");
    }

    // ==================== 基础断言测试 ====================

    @Test
    @DisplayName("测试 - 相等断言")
    void testEqualAssertion() {
        int expected = 10;
        int actual = 5 + 5;
        assertEquals(expected, actual, "数值应该相等");
    }

    @Test
    @DisplayName("测试 - 字符串断言")
    void testStringAssertion() {
        String text = "Hello, Agent!";
        assertTrue(text.contains("Agent"), "字符串应包含 'Agent'");
        assertFalse(text.isEmpty(), "字符串不应为空");
        assertEquals(13, text.length(), "字符串长度应为13");
    }

    @Test
    @DisplayName("测试 - 对象非空断言")
    void testNotNullAssertion() {
        Object obj = new Object();
        assertNotNull(obj, "对象不应为 null");
    }

    // ==================== 异常测试 ====================

    @Test
    @DisplayName("测试 - 异常抛出")
    void testExceptionThrown() {
        assertThrows(IllegalArgumentException.class, () -> {
            throw new IllegalArgumentException("测试异常");
        });
    }

    @Test
    @DisplayName("测试 - 异常消息验证")
    void testExceptionMessage() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            throw new IllegalArgumentException("错误消息");
        });
        assertEquals("错误消息", exception.getMessage());
    }

    // ==================== 分组测试 ====================

    @Nested
    @DisplayName("数学运算测试组")
    class MathTests {

        @Test
        @DisplayName("测试加法")
        void testAddition() {
            assertEquals(10, 5 + 5);
            assertEquals(100, 50 + 50);
        }

        @Test
        @DisplayName("测试减法")
        void testSubtraction() {
            assertEquals(0, 5 - 5);
            assertEquals(-5, 0 - 5);
        }

        @Test
        @DisplayName("测试乘法")
        void testMultiplication() {
            assertEquals(25, 5 * 5);
            assertEquals(0, 0 * 100);
        }
    }

    @Nested
    @DisplayName("字符串处理测试组")
    class StringTests {

        @Test
        @DisplayName("测试字符串拼接")
        void testConcatenation() {
            assertEquals("Hello World", "Hello" + " " + "World");
        }

        @Test
        @DisplayName("测试字符串转换")
        void testConversion() {
            assertEquals("HELLO", "hello".toUpperCase());
            assertEquals("hello", "HELLO".toLowerCase());
        }

        @Test
        @DisplayName("测试字符串修剪")
        void testTrim() {
            assertEquals("test", "  test  ".trim());
        }
    }

    // ==================== 数组测试 ====================

    @Test
    @DisplayName("测试 - 数组内容")
    void testArrayContent() {
        int[] numbers = {1, 2, 3, 4, 5};
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, numbers);
    }

    // ==================== 超时测试 ====================

    @Test
    @DisplayName("测试 - 执行超时")
    void testTimeout() {
        // 模拟快速执行的操作
        assertTrue(true);
    }

    // ==================== 禁用测试 ====================

    @Test
    @Disabled("此测试暂时禁用")
    @DisplayName("测试 - 禁用的测试")
    void testDisabled() {
        fail("这个测试不应该执行");
    }

    // ==================== 重复测试 ====================

    @RepeatedTest(3)
    @DisplayName("测试 - 重复执行 3 次")
    void testRepeated() {
        // 简单重复测试，不需要 RepetitionInfo 参数
        assertTrue(true);
    }
}
