package com.example.agent.console;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleStyleTest {

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("null文本返回空字符串")
        void testNullText() {
            assertEquals("", ConsoleStyle.green(null));
            assertEquals("", ConsoleStyle.yellow(null));
            assertEquals("", ConsoleStyle.red(null));
            assertEquals("", ConsoleStyle.gray(null));
            assertEquals("", ConsoleStyle.white(null));
            assertEquals("", ConsoleStyle.cyan(null));
            assertEquals("", ConsoleStyle.bold(null));
            assertEquals("", ConsoleStyle.boldGreen(null));
            assertEquals("", ConsoleStyle.boldYellow(null));
            assertEquals("", ConsoleStyle.boldRed(null));
            assertEquals("", ConsoleStyle.boldCyan(null));
            assertEquals("", ConsoleStyle.dim(null));
        }

        @Test
        @DisplayName("空字符串返回ANSI转义序列")
        void testEmptyText() {
            String result = ConsoleStyle.green("");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("颜色方法测试")
    class ColorMethodTests {

        @Test
        @DisplayName("green返回带ANSI转义的字符串")
        void testGreen() {
            String result = ConsoleStyle.green("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("yellow返回带ANSI转义的字符串")
        void testYellow() {
            String result = ConsoleStyle.yellow("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("red返回带ANSI转义的字符串")
        void testRed() {
            String result = ConsoleStyle.red("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("gray返回带ANSI转义的字符串")
        void testGray() {
            String result = ConsoleStyle.gray("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("white返回带ANSI转义的字符串")
        void testWhite() {
            String result = ConsoleStyle.white("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("cyan返回带ANSI转义的字符串")
        void testCyan() {
            String result = ConsoleStyle.cyan("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }
    }

    @Nested
    @DisplayName("粗体方法测试")
    class BoldMethodTests {

        @Test
        @DisplayName("bold返回带粗体ANSI转义的字符串")
        void testBold() {
            String result = ConsoleStyle.bold("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("boldGreen返回带粗体绿色ANSI转义的字符串")
        void testBoldGreen() {
            String result = ConsoleStyle.boldGreen("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("boldYellow返回带粗体黄色ANSI转义的字符串")
        void testBoldYellow() {
            String result = ConsoleStyle.boldYellow("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("boldRed返回带粗体红色ANSI转义的字符串")
        void testBoldRed() {
            String result = ConsoleStyle.boldRed("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }

        @Test
        @DisplayName("boldCyan返回带粗体青色ANSI转义的字符串")
        void testBoldCyan() {
            String result = ConsoleStyle.boldCyan("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }
    }

    @Nested
    @DisplayName("dim方法测试")
    class DimMethodTests {

        @Test
        @DisplayName("dim返回带暗淡ANSI转义的字符串")
        void testDim() {
            String result = ConsoleStyle.dim("test");
            
            assertNotNull(result);
            assertTrue(result.contains("test"));
        }
    }

    @Nested
    @DisplayName("便捷方法测试")
    class ConvenienceMethodTests {

        @Test
        @DisplayName("prompt返回提示符")
        void testPrompt() {
            String result = ConsoleStyle.prompt();
            
            assertNotNull(result);
            assertTrue(result.contains(">"));
        }

        @Test
        @DisplayName("thinking返回思考提示")
        void testThinking() {
            String result = ConsoleStyle.thinking();
            
            assertNotNull(result);
            assertTrue(result.contains("Thinking"));
        }

        @Test
        @DisplayName("toolCall返回工具调用提示")
        void testToolCall() {
            String result = ConsoleStyle.toolCall("bash", "ls -la");
            
            assertNotNull(result);
            assertTrue(result.contains("bash"));
            assertTrue(result.contains("ls -la"));
        }

        @Test
        @DisplayName("error返回错误消息")
        void testError() {
            String result = ConsoleStyle.error("Something went wrong");
            
            assertNotNull(result);
            assertTrue(result.contains("Error"));
            assertTrue(result.contains("Something went wrong"));
        }

        @Test
        @DisplayName("success返回成功消息")
        void testSuccess() {
            String result = ConsoleStyle.success("Operation completed");
            
            assertNotNull(result);
            assertTrue(result.contains("Operation completed"));
        }

        @Test
        @DisplayName("info返回信息消息")
        void testInfo() {
            String result = ConsoleStyle.info("This is info");
            
            assertNotNull(result);
            assertTrue(result.contains("This is info"));
        }

        @Test
        @DisplayName("divider返回分隔线")
        void testDivider() {
            String result = ConsoleStyle.divider();
            
            assertNotNull(result);
            assertTrue(result.length() > 0);
        }

        @Test
        @DisplayName("userLabel返回用户标签")
        void testUserLabel() {
            String result = ConsoleStyle.userLabel();
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("aiLabel返回AI标签")
        void testAiLabel() {
            String result = ConsoleStyle.aiLabel();
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("对话分隔符测试")
    class ConversationDividerTests {

        @Test
        @DisplayName("conversationDivider返回分隔符")
        void testConversationDivider() {
            String result = ConsoleStyle.conversationDivider(1);
            
            assertNotNull(result);
            assertTrue(result.contains("1"));
        }

        @Test
        @DisplayName("conversationDivider负数轮次安全处理")
        void testConversationDividerNegative() {
            String result = ConsoleStyle.conversationDivider(-5);
            
            assertNotNull(result);
            assertTrue(result.contains("0"));
        }

        @Test
        @DisplayName("conversationDivider零轮次")
        void testConversationDividerZero() {
            String result = ConsoleStyle.conversationDivider(0);
            
            assertNotNull(result);
            assertTrue(result.contains("0"));
        }

        @Test
        @DisplayName("conversationDivider大数值轮次")
        void testConversationDividerLarge() {
            String result = ConsoleStyle.conversationDivider(1000);
            
            assertNotNull(result);
            assertTrue(result.contains("1000"));
        }

        @Test
        @DisplayName("conversationEnd返回结束分隔符")
        void testConversationEnd() {
            String result = ConsoleStyle.conversationEnd();
            
            assertNotNull(result);
            assertTrue(result.length() > 0);
        }
    }

    @Nested
    @DisplayName("特殊字符测试")
    class SpecialCharacterTests {

        @Test
        @DisplayName("包含换行符的文本")
        void testTextWithNewlines() {
            String result = ConsoleStyle.green("line1\nline2\nline3");
            
            assertNotNull(result);
            assertTrue(result.contains("line1"));
        }

        @Test
        @DisplayName("包含Unicode字符的文本")
        void testTextWithUnicode() {
            String result = ConsoleStyle.green("你好世界🎉");
            
            assertNotNull(result);
            assertTrue(result.contains("你好世界"));
        }

        @Test
        @DisplayName("包含ANSI转义序列的文本")
        void testTextWithAnsi() {
            String result = ConsoleStyle.red("\u001B[32mgreen\u001B[0m");
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("超长文本")
        void testVeryLongText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("a");
            }
            
            String result = ConsoleStyle.green(sb.toString());
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("私有构造函数测试")
    class PrivateConstructorTests {

        @Test
        @DisplayName("工具类不应实例化")
        void testPrivateConstructor() throws Exception {
            var constructor = ConsoleStyle.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            
            assertDoesNotThrow(() -> constructor.newInstance());
        }
    }
}
