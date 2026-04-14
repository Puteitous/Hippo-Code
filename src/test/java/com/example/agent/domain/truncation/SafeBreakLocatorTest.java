package com.example.agent.domain.truncation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SafeBreakLocator 边界条件测试
 *
 * 测试重点：
 * - 数组边界处理
 * - 极端参数值
 * - 各种安全断点规则
 */
class SafeBreakLocatorTest {

    private SafeBreakLocator breakLocator;

    @BeforeEach
    void setUp() {
        breakLocator = new SafeBreakLocator();
    }

    @Test
    @DisplayName("边界 - targetLine = 0 返回 0")
    void testTargetLineZero() {
        String[] lines = {"line1", "line2", "line3"};
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 0);

        assertTrue(result.isPresent());
        assertEquals(0, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - targetLine 负数返回 0")
    void testTargetLineNegative() {
        String[] lines = {"line1", "line2", "line3"};
        OptionalInt result = breakLocator.findSafeBreakLine(lines, -5);

        assertTrue(result.isPresent());
        assertEquals(0, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - targetLine 超出数组长度返回 lines.length")
    void testTargetLineBeyondLength() {
        String[] lines = {"line1", "line2", "line3"};
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 100);

        assertTrue(result.isPresent());
        assertEquals(3, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - targetLine 刚好等于数组长度")
    void testTargetLineEqualsLength() {
        String[] lines = {"line1", "line2", "line3"};
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 3);

        assertTrue(result.isPresent());
        assertEquals(3, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - 空数组处理")
    void testEmptyArray() {
        String[] lines = {};
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 0);

        assertTrue(result.isPresent());
        assertEquals(0, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - 单行数组")
    void testSingleLineArray() {
        String[] lines = {"public class Test {}"};
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 1);

        assertTrue(result.isPresent());
        assertEquals(1, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - searchBackLines = 0 仅检查目标行")
    void testZeroSearchBackLines() {
        String[] lines = {
            "public class Test {",
            "    ",
            "    public void method() {}",
            "}"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 2, 0);

        assertTrue(result.isPresent());
        assertEquals(3, result.getAsInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MAX_VALUE, 1000, 100})
    @DisplayName("边界 - 极大 searchBackLines 安全处理")
    void testVeryLargeSearchBackLines(int searchBackLines) {
        String[] lines = {"line1", "line2", "line3", "line4", "line5"};

        assertDoesNotThrow(() -> {
            OptionalInt result = breakLocator.findSafeBreakLine(lines, 3, searchBackLines);
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("边界 - 空行作为安全断点")
    void testEmptyLineBreak() {
        String[] lines = {
            "public class Test {",
            "    public void method1() {",
            "        // code",
            "    }",
            "",
            "    public void method2() {"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 5);

        assertTrue(result.isPresent());
        assertEquals(6, result.getAsInt(), "findSafeBreakLine 返回 i+1（下一行开始）");
    }

    @Test
    @DisplayName("边界 - 右大括号作为安全断点")
    void testClosingBraceBreak() {
        String[] lines = {
            "public class Test {",
            "    public void method() {",
            "        int x = 1;",
            "    }",
            "    // next method"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 4);

        assertTrue(result.isPresent());
        assertEquals(4, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - 分号结尾作为安全断点")
    void testSemicolonBreak() {
        String[] lines = {
            "public class Test {",
            "    int x = 1;",
            "    int y = 2;"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 3);

        assertTrue(result.isPresent());
        assertEquals(3, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - class 定义作为安全断点")
    void testClassDefinitionBreak() {
        String[] lines = {
            "package com.example;",
            "",
            "public class Test {",
            "    // code"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 3);

        assertTrue(result.isPresent());
        assertEquals(3, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - 方法定义作为安全断点")
    void testMethodDefinitionBreak() {
        String[] lines = {
            "public class Test {",
            "",
            "    public void method() {",
            "        // code"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 3);

        assertTrue(result.isPresent());
        assertEquals(3, result.getAsInt());
    }

    @Test
    @DisplayName("边界 - 找不到安全断点时返回 Optional.empty()")
    void testNoSafeBreakFound() {
        String[] lines = {
            "        int x = 1 + 2",
            "            + 3 + 4",
            "            + 5 + 6",
            "            + 7 + 8;"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 2, 1);

        assertFalse(result.isPresent(), "连续表达式中间应找不到安全断点");
    }

    @Test
    @DisplayName("边界 - 向后搜索找到断点")
    void testSearchBackFindsBreak() {
        String[] lines = {
            "    }",
            "    int x = 1 + 2",
            "        + 3 + 4",
            "        + 5 + 6"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 3, 4);

        assertTrue(result.isPresent());
        assertEquals(1, result.getAsInt(), "应向后搜索找到右大括号断点");
    }

    @Test
    @DisplayName("边界 - 优先使用规则匹配的断点而非空行")
    void testRuleBreakPreferredOverEmptyLine() {
        String[] lines = {
            "",
            "    }",
            "    some code here"
        };
        OptionalInt result = breakLocator.findSafeBreakLine(lines, 2, 2);

        assertTrue(result.isPresent());
        assertEquals(2, result.getAsInt(), "应优先匹配右大括号断点");
    }

    @Test
    @DisplayName("边界 - 默认搜索距离生效")
    void testDefaultSearchDistance() {
        String[] lines = new String[20];
        for (int i = 0; i < 20; i++) {
            lines[i] = "    code line " + i;
        }
        lines[5] = "    }";

        OptionalInt result = breakLocator.findSafeBreakLine(lines, 15);

        assertFalse(result.isPresent(), "默认搜索距离内（8行）应找不到第5行的断点");
    }

    @Test
    @DisplayName("边界 - searchBackLines 负数按 0 处理")
    void testNegativeSearchBackLines() {
        String[] lines = {"line1", "    }", "line3"};

        assertDoesNotThrow(() -> {
            OptionalInt result = breakLocator.findSafeBreakLine(lines, 2, -5);
            assertNotNull(result);
        });
    }
}
