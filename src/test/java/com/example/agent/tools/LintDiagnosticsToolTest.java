package com.example.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖重写后的 LintDiagnosticsTool：核心逻辑为 shortenPath、语言推断、
 * 以及 Diagnostic 内部类。底层语法解析委托给 TreeSitterWasmParser 测试。
 */
@DisplayName("LintDiagnosticsTool 逻辑测试")
class LintDiagnosticsToolTest {

    private final LintDiagnosticsTool tool = new LintDiagnosticsTool();

    // ==================== shortenPath ====================

    @Nested
    @DisplayName("shortenPath 路径缩短")
    class ShortenPathTest {

        @Test
        @DisplayName("含 /src/ 的路径 → 从 /src/ 开始截取")
        void testWithSrcPrefix() {
            assertEquals("/src/main/java/com/example/Test.java",
                LintDiagnosticsTool.shortenPath("/home/user/project/src/main/java/com/example/Test.java"));
        }

        @Test
        @DisplayName("含 \\src\\ 的路径（Windows）→ 从 \\src\\ 开始截取")
        void testWithSrcBackslash() {
            assertEquals("\\src\\main\\java\\Test.java",
                LintDiagnosticsTool.shortenPath("C:\\Users\\test\\project\\src\\main\\java\\Test.java"));
        }

        @Test
        @DisplayName("不含 /src/ → 取最后两级")
        void testWithoutSrc() {
            assertEquals("example/Test.java",
                LintDiagnosticsTool.shortenPath("/home/user/project/example/Test.java"));
        }

        @Test
        @DisplayName("短路径（不到两级）→ 原样返回")
        void testShortPath() {
            assertEquals("Test.java", LintDiagnosticsTool.shortenPath("Test.java"));
            assertEquals("dir/Test.java", LintDiagnosticsTool.shortenPath("dir/Test.java"));
        }

        @Test
        @DisplayName("null → 空字符串")
        void testNullPath() {
            assertEquals("", LintDiagnosticsTool.shortenPath(null));
        }

        @Test
        @DisplayName("空字符串 → 空字符串")
        void testEmptyPath() {
            assertEquals("", LintDiagnosticsTool.shortenPath(""));
        }
    }

    // ==================== LANGUAGE_EXTENSIONS ====================

    @Nested
    @DisplayName("LANGUAGE_EXTENSIONS 语言扩展名映射")
    class LanguageExtensionsTest {

        @Test
        @DisplayName("所有语言都有定义的扩展名")
        void testAllLanguagesHaveExtensions() {
            Map<String, List<String>> exts = LintDiagnosticsTool.LANGUAGE_EXTENSIONS;
            assertFalse(exts.isEmpty());
            assertTrue(exts.containsKey("java"));
            assertTrue(exts.containsKey("javascript"));
            assertTrue(exts.containsKey("typescript"));
            assertTrue(exts.containsKey("python"));
            assertTrue(exts.containsKey("go"));
            assertTrue(exts.containsKey("rust"));
            assertTrue(exts.containsKey("html"));
            assertTrue(exts.containsKey("css"));
            assertTrue(exts.containsKey("json"));
        }

        @Test
        @DisplayName("Java → .java")
        void testJavaExtension() {
            assertTrue(LintDiagnosticsTool.LANGUAGE_EXTENSIONS.get("java").contains(".java"));
        }

        @Test
        @DisplayName("JavaScript → .js／.mjs／.cjs／.jsx")
        void testJavaScriptExtensions() {
            List<String> exts = LintDiagnosticsTool.LANGUAGE_EXTENSIONS.get("javascript");
            assertTrue(exts.containsAll(List.of(".js", ".mjs", ".cjs", ".jsx")));
        }

        @Test
        @DisplayName("TypeScript → .ts／.tsx")
        void testTypeScriptExtensions() {
            List<String> exts = LintDiagnosticsTool.LANGUAGE_EXTENSIONS.get("typescript");
            assertTrue(exts.containsAll(List.of(".ts", ".tsx")));
        }
    }

    // ==================== Diagnostic 内部类 ====================

    @Nested
    @DisplayName("Diagnostic 内部类")
    class DiagnosticTest {

        @Test
        @DisplayName("构造函数和字段访问")
        void testConstructor() {
            var d = new LintDiagnosticsTool.Diagnostic("Test.java", 5, 3, "error", "';' expected");
            assertEquals("Test.java", d.file);
            assertEquals(5, d.line);
            assertEquals(3, d.column);
            assertEquals("error", d.severity);
            assertEquals("';' expected", d.message);
        }
    }
}
