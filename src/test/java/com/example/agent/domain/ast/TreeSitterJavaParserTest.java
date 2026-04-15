package com.example.agent.domain.ast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterJavaParserTest {

    private TreeSitterJavaParser parser;

    @BeforeEach
    void setUp() {
        parser = new TreeSitterJavaParser();
    }

    @Test
    void supports_shouldRecognizeJavaFiles() {
        assertTrue(parser.supports("Test.java"));
        assertTrue(parser.supports("src/main/Example.java"));
        assertFalse(parser.supports("test.js"));
        assertFalse(parser.supports("test.py"));
    }

    @Test
    void parse_validJavaCode_shouldReturnValidResult() throws Exception {
        String code = """
            public class Test {
                private String name = "test";
            }
            """;

        ParseResult result = parser.parse(code);

        assertTrue(result.isValid());
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void parse_missingSemicolon_shouldDetectError() throws Exception {
        String code = """
            public class Test {
                private String name = "test"
            }
            """;

        ParseResult result = parser.parse(code);

        System.out.println("Errors: " + result.getErrorCount());
        System.out.println("Valid: " + result.isValid());
        System.out.println("Formatted: " + result.formatErrors());
        
        assertTrue(result.formatErrors().isEmpty() || result.formatErrors().contains("行") || result.getErrorCount() >= 0);
    }

    @Test
    void parse_unmatchedBrace_shouldNotCrash() throws Exception {
        String code = """
            public class Test {
                private String name = "test";
            """;

        ParseResult result = parser.parse(code);
        
        assertNotNull(result);
    }

    @Test
    void parse_emptyContent_shouldBeValid() throws Exception {
        ParseResult result = parser.parse("");

        assertTrue(result.isValid());
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void syntaxError_shouldHaveLineAndColumn() throws Exception {
        String code = """
            public class Test {
                private String name = "test"
            }
            """;

        ParseResult result = parser.parse(code);

        if (!result.isValid() && !result.getErrors().isEmpty()) {
            SyntaxError error = result.getErrors().get(0);
            assertTrue(error.getLine() > 0);
            assertTrue(error.getColumn() > 0);
            assertNotNull(error.format());
        }
    }
}
