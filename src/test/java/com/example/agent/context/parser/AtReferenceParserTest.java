package com.example.agent.context.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AtReferenceParser 测试
 */
class AtReferenceParserTest {

    private AtReferenceParser parser;

    @BeforeEach
    void setUp() {
        parser = new AtReferenceParser();
    }

    @Test
    void testParseSingleReference() {
        String input = "请分析 @src/main/java/Test.java 文件";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertEquals(1, references.size());
        assertEquals("src/main/java/Test.java", references.get(0));
    }

    @Test
    void testParseMultipleReferences() {
        String input = "分析 @file1.java 和 @file2.java 以及 @dir/file3.java";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertEquals(3, references.size());
        assertEquals("file1.java", references.get(0));
        assertEquals("file2.java", references.get(1));
        assertEquals("dir/file3.java", references.get(2));
    }

    @Test
    void testParseRelativePaths() {
        String input = "分析 @./relative/path.java 和 @../parent/path.java";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertEquals(2, references.size());
        assertEquals("./relative/path.java", references.get(0));
        assertEquals("../parent/path.java", references.get(1));
    }

    @Test
    void testParseNoReferences() {
        String input = "普通输入，没有 @ 引用";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertTrue(references.isEmpty());
    }

    @Test
    void testParseEmptyInput() {
        List<String> references = parser.parse("");

        assertNotNull(references);
        assertTrue(references.isEmpty());
    }

    @Test
    void testParseNullInput() {
        List<String> references = parser.parse(null);

        assertNotNull(references);
        assertTrue(references.isEmpty());
    }

    @Test
    void testParseMixedContent() {
        String input = "请分析 @src/File.java 中的方法，以及 @test/Test.java 中的测试";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertEquals(2, references.size());
        assertEquals("src/File.java", references.get(0));
        assertEquals("test/Test.java", references.get(1));
    }

    @Test
    void testIsValidPath() {
        // 相对路径应该有效
        assertTrue(parser.isValidPath("path/to/file"));
        assertTrue(parser.isValidPath("./relative/path"));
        assertTrue(parser.isValidPath("../parent/path"));

        // 绝对路径应该无效
        assertFalse(parser.isValidPath("/absolute/path"));
        assertFalse(parser.isValidPath("C:/Windows/path"));
    }

    @Test
    void testCleanupReference() {
        assertEquals("path/to/file", parser.cleanupReference("@path/to/file"));
        assertEquals("file.java", parser.cleanupReference("@file.java"));
        assertNull(parser.cleanupReference(null));
    }

    @Test
    void testContainsReferences() {
        assertTrue(parser.containsReferences("分析 @file.java"));
        assertFalse(parser.containsReferences("普通输入"));
        assertFalse(parser.containsReferences(""));
        assertFalse(parser.containsReferences(null));
    }

    @Test
    void testParseComplexPaths() {
        String input = "分析 @src/main/java/com/example/agent/context/parser/AtReferenceParser.java";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertEquals(1, references.size());
        assertEquals("src/main/java/com/example/agent/context/parser/AtReferenceParser.java", references.get(0));
    }

    @Test
    void testParsePathsWithDashes() {
        String input = "分析 @src/main/java/com/example/agent/context/memory/Warm-Memory.java";
        List<String> references = parser.parse(input);

        assertNotNull(references);
        assertEquals(1, references.size());
        assertEquals("src/main/java/com/example/agent/context/memory/Warm-Memory.java", references.get(0));
    }
}
