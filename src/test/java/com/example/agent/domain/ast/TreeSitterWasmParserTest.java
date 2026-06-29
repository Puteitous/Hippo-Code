package com.example.agent.domain.ast;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TreeSitterWasmParser 集成测试。
 * 依赖 resources/tree-sitter/tree-sitter-parser.wasm 文件。
 * 如果 WASM 不可用，所有测试通过 assumeTrue 自动跳过。
 */
@DisplayName("TreeSitterWasmParser WASM 解析测试")
class TreeSitterWasmParserTest {

    @BeforeAll
    static void checkWasmAvailable() {
        assumeTrue(TreeSitterWasmParser.isAvailable(),
            "tree-sitter-parser.wasm 未加载，跳过所有测试");
    }

    // ==================== WASM 加载 ====================

    @Nested
    @DisplayName("WASM 加载")
    class WasmLoadingTest {

        @Test
        @DisplayName("isAvailable() 返回 true")
        void isAvailable() {
            assertTrue(TreeSitterWasmParser.isAvailable());
        }

        @Test
        @DisplayName("hasAnyAvailable() 返回 true")
        void hasAnyAvailable() {
            assertTrue(TreeSitterWasmParser.hasAnyAvailable());
        }

        @Test
        @DisplayName("forFile() 根据路径返回对应 parser")
        void forFile() {
            assertNotNull(TreeSitterWasmParser.forFile("Test.java"));
            assertNotNull(TreeSitterWasmParser.forFile("test.py"));
            assertNull(TreeSitterWasmParser.forFile("unknown.xyz"));
            assertNull(TreeSitterWasmParser.forFile(null));
        }
    }

    // ==================== 正确代码（9 种语言） ====================

    @Nested
    @DisplayName("正确代码解析 — 9 种语言")
    class ValidCodeTest {

        @Test
        @DisplayName("Java — 合法代码")
        void java() throws Exception {
            var parser = new TreeSitterWasmParser("java");
            String code = """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello, world!");
                    }
                }
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid(), "Java 合法代码应通过检查");
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("JavaScript — 合法代码")
        void javaScript() throws Exception {
            var parser = new TreeSitterWasmParser("javascript");
            String code = """
                function greet(name) {
                    return "Hello, " + name + "!";
                }
                module.exports = { greet };
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("TypeScript — 合法代码")
        void typeScript() throws Exception {
            var parser = new TreeSitterWasmParser("typescript");
            String code = """
                interface Person {
                    name: string;
                    age: number;
                }
                const user: Person = { name: "Alice", age: 30 };
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("TSX — 合法代码")
        void tsx() throws Exception {
            var parser = new TreeSitterWasmParser("tsx");
            String code = """
                const App: React.FC = () => {
                    return <div>Hello</div>;
                };
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("Python — 合法代码")
        void python() throws Exception {
            var parser = new TreeSitterWasmParser("python");
            String code = """
                def greet(name: str) -> str:
                    return f"Hello, {name}!"
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("Go — 合法代码")
        void go() throws Exception {
            var parser = new TreeSitterWasmParser("go");
            String code = """
                package main
                import "fmt"
                func main() {
                    fmt.Println("Hello, world!")
                }
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("Rust — 合法代码")
        void rust() throws Exception {
            var parser = new TreeSitterWasmParser("rust");
            String code = """
                fn main() {
                    println!("Hello, world!");
                }
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("HTML — 合法代码")
        void html() throws Exception {
            var parser = new TreeSitterWasmParser("html");
            String code = """
                <!DOCTYPE html>
                <html>
                <head><title>Test</title></head>
                <body><p>Hello</p></body>
                </html>
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("CSS — 合法代码")
        void css() throws Exception {
            var parser = new TreeSitterWasmParser("css");
            String code = """
                .container {
                    display: flex;
                    justify-content: center;
                }
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("JSON — 合法代码")
        void json() throws Exception {
            var parser = new TreeSitterWasmParser("json");
            String code = """
                {
                    "name": "test",
                    "version": "1.0.0",
                    "list": [1, 2, 3]
                }
                """;
            ParseResult result = parser.parse(code);
            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }
    }

    // ==================== 错误代码检测 ====================

    @Nested
    @DisplayName("错误代码检测")
    class InvalidCodeTest {

        @Test
        @DisplayName("Java — 缺少分号")
        void javaMissingSemicolon() throws Exception {
            var parser = new TreeSitterWasmParser("java");
            String code = """
                public class Test {
                    private String name = "test"
                }
                """;
            ParseResult result = parser.parse(code);
            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("Java — 缺少闭合花括号")
        void javaMissingBrace() throws Exception {
            var parser = new TreeSitterWasmParser("java");
            String code = """
                public class Test {
                    public void run() {
                        int x = 1;
                }
                """;
            ParseResult result = parser.parse(code);
            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("JavaScript — 缺少闭合圆括号")
        void jsMissingParen() throws Exception {
            var parser = new TreeSitterWasmParser("javascript");
            String code = """
                function greet(name {
                    return "Hello";
                }
                """;
            ParseResult result = parser.parse(code);
            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("Python — 缺少冒号")
        void pythonMissingColon() throws Exception {
            var parser = new TreeSitterWasmParser("python");
            String code = """
                def greet(name)
                    return f"Hello, {name}!"
                """;
            ParseResult result = parser.parse(code);
            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("JSON — 缺少逗号")
        void jsonMissingComma() throws Exception {
            var parser = new TreeSitterWasmParser("json");
            String code = """
                {
                    "a": 1
                    "b": 2
                }
                """;
            ParseResult result = parser.parse(code);
            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
        }
    }

    // ==================== 语言映射 ====================

    @Nested
    @DisplayName("语言映射（normalize/resolve）")
    class LanguageMappingTest {

        @Test
        @DisplayName("js → javascript，通过解析验证合法性")
        void jsAlias() throws Exception {
            var parser = new TreeSitterWasmParser("js");
            assertEquals("javascript", parser.language());
            String code = "const x = 1;";
            assertTrue(parser.parse(code).isValid());
        }

        @Test
        @DisplayName("ts → typescript")
        void tsAlias() throws Exception {
            var parser = new TreeSitterWasmParser("ts");
            assertEquals("typescript", parser.language());
        }

        @Test
        @DisplayName("py → python")
        void pyAlias() throws Exception {
            var parser = new TreeSitterWasmParser("py");
            assertEquals("python", parser.language());
        }

        @Test
        @DisplayName("rs → rust")
        void rsAlias() throws Exception {
            var parser = new TreeSitterWasmParser("rs");
            assertEquals("rust", parser.language());
        }

        @Test
        @DisplayName("htm → html")
        void htmAlias() throws Exception {
            var parser = new TreeSitterWasmParser("htm");
            assertEquals("html", parser.language());
        }

        @Test
        @DisplayName("golang → go")
        void golangAlias() throws Exception {
            var parser = new TreeSitterWasmParser("golang");
            assertEquals("go", parser.language());
        }

        @Test
        @DisplayName("scss → css")
        void scssAlias() throws Exception {
            var parser = new TreeSitterWasmParser("scss");
            assertEquals("css", parser.language());
        }

        @Test
        @DisplayName("less → css")
        void lessAlias() throws Exception {
            var parser = new TreeSitterWasmParser("less");
            assertEquals("css", parser.language());
        }

        @Test
        @DisplayName("null → java（默认）")
        void nullDefault() throws Exception {
            var parser = new TreeSitterWasmParser(null);
            assertEquals("java", parser.language());
        }

        @Test
        @DisplayName("不支持的语言 → 返回错误结果")
        void unsupportedLanguage() throws Exception {
            var parser = new TreeSitterWasmParser("kotlin");
            assertEquals("kotlin", parser.language());
            // WASM 返回 error 响应，应被识别为无效
            ParseResult result = parser.parse("fun main() {}");
            assertFalse(result.isValid(), "不支持的语音应返回 invalid");
            assertFalse(result.getErrors().isEmpty());
            assertTrue(result.getErrors().get(0).getMessage().contains("kotlin"));
        }
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("边界条件")
    class BoundaryTest {

        @Test
        @DisplayName("空内容 → valid=true")
        void emptyContent() throws Exception {
            var parser = new TreeSitterWasmParser("java");
            assertTrue(parser.parse("").isValid());
            assertTrue(parser.parse("  ").isValid());
            assertTrue(parser.parse("\n").isValid());
        }

        @Test
        @DisplayName("null 内容 → valid=true")
        void nullContent() throws Exception {
            var parser = new TreeSitterWasmParser("java");
            assertTrue(parser.parse(null).isValid());
        }

        @Test
        @DisplayName("support() 方法 — 匹配扩展名")
        void supports() {
            var javaParser = new TreeSitterWasmParser("java");
            assertTrue(javaParser.supports("Test.java"));
            assertFalse(javaParser.supports("test.py"));
            assertFalse(javaParser.supports(null));
        }
    }
}
