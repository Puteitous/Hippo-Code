package com.example.agent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FrontmatterParser 单元测试
 */
class FrontmatterParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseFrontmatter() throws IOException {
        String content = """
            ---
            id: 550e8400-e29b-41d4-a716-446655440000
            type: USER_PREFERENCE
            scope: project
            tags: java, yaml, configuration
            created_at: 2024-01-15T10:30:00Z
            last_accessed: 2024-01-15T10:30:00Z
            access_count: 5
            ---
            
            # User Prefers YAML
            
            Java projects should use YAML configuration.
            """;

        Map<String, Object> frontmatter = FrontmatterParser.parseContent(content);

        assertEquals("550e8400-e29b-41d4-a716-446655440000", frontmatter.get("id"));
        assertEquals("USER_PREFERENCE", frontmatter.get("type"));
        assertEquals("project", frontmatter.get("scope"));
        assertEquals(5, ((Number) frontmatter.get("access_count")).intValue());
    }

    @Test
    void testParseContentWithoutFrontmatter() {
        String content = "# Just content without frontmatter";
        Map<String, Object> frontmatter = FrontmatterParser.parseContent(content);
        assertTrue(frontmatter.isEmpty());
    }

    @Test
    void testParseIncompleteFrontmatter() {
        String content = """
            ---
            id: test-id
            # Missing closing separator
            Content here
            """;
        
        Map<String, Object> frontmatter = FrontmatterParser.parseContent(content);
        assertTrue(frontmatter.isEmpty());
    }

    @Test
    void testGenerateFrontmatter() {
        Set<String> tags = new HashSet<>();
        tags.add("java");
        tags.add("yaml");
        
        MemoryEntry entry = new MemoryEntry(
            "550e8400-e29b-41d4-a716-446655440000",
            "# Test Content",
            MemoryEntry.MemoryType.USER_PREFERENCE,
            tags
        );

        String frontmatter = FrontmatterParser.generate(entry);

        assertTrue(frontmatter.startsWith("---"));
        assertTrue(frontmatter.contains("id: 550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(frontmatter.contains("type: USER_PREFERENCE"));
        assertTrue(frontmatter.contains("scope: project"));
        assertTrue(frontmatter.contains("tags: java, yaml") || frontmatter.contains("tags: yaml, java"));
    }

    @Test
    void testGenerateIndexLine() {
        MemoryEntry entry = new MemoryEntry(
            "550e8400-e29b-41d4-a716-446655440000",
            "# Spring Security Configuration\n\nUse JWT with 24h expiry",
            MemoryEntry.MemoryType.PROJECT_CONTEXT,
            new HashSet<>()
        );

        String indexLine = FrontmatterParser.generateIndexLine(entry);

        assertTrue(indexLine.startsWith("- ["));
        assertTrue(indexLine.contains("](550e8400-e29b-41d4-a716-446655440000.md)"));
        assertTrue(indexLine.contains("—"));
        assertTrue(indexLine.length() <= 200); // 标题 + 钩子不应太长
    }

    @Test
    void testExtractTitle() {
        String contentWithHash = "# My Title\nContent here";
        String title = FrontmatterParser.extractTitle(contentWithHash);
        assertEquals("My Title", title);

        String contentWithoutHash = "First line content\nSecond line";
        title = FrontmatterParser.extractTitle(contentWithoutHash);
        assertEquals("First line content", title);

        String longLine = "A".repeat(100);
        title = FrontmatterParser.extractTitle(longLine);
        assertEquals(53, title.length()); // 50 + "..."
        assertTrue(title.endsWith("..."));
    }

    @Test
    void testGenerateHook() {
        String content = "# Title\nThis is the content with **markdown** and `code`.";
        String hook = FrontmatterParser.generateHook(content);
        
        assertFalse(hook.contains("#"));
        assertFalse(hook.contains("**"));
        assertFalse(hook.contains("`"));
        assertTrue(hook.length() <= 150);
    }

    @Test
    void testGenerateHook_LongContent() {
        String content = "A".repeat(200);
        String hook = FrontmatterParser.generateHook(content);
        
        assertEquals(150, hook.length());
        assertTrue(hook.endsWith("..."));
    }

    @Test
    void testParseEntryFromFile() throws IOException {
        String content = """
            ---
            id: 550e8400-e29b-41d4-a716-446655440000
            type: PROJECT_CONTEXT
            scope: project
            tags: spring, security
            ---
            
            # Spring Security Decision
            
            Use JWT authentication.
            """;

        Path file = tempDir.resolve("test.md");
        Files.writeString(file, content);

        MemoryEntry entry = FrontmatterParser.parseEntry(file);

        assertEquals("550e8400-e29b-41d4-a716-446655440000", entry.getId());
        assertEquals(MemoryEntry.MemoryType.PROJECT_CONTEXT, entry.getType());
        assertEquals("project", entry.getScope());
        assertTrue(entry.getContent().contains("JWT"));
    }

    @Test
    void testParseEntry_NoFrontmatter() throws IOException {
        String content = "# Just Content\n\nNo frontmatter here.";
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, content);

        MemoryEntry entry = FrontmatterParser.parseEntry(file);

        assertNotNull(entry.getId()); // 应该生成 UUID
        assertEquals("Just Content", entry.getContent().split("\n")[0].trim());
    }

    @Test
    void testExtractBody() {
        String content = """
            ---
            id: test
            ---
            
            This is the body content.
            Multiple lines.
            """;

        String body = FrontmatterParser.extractBody(content);
        assertTrue(body.contains("This is the body content"));
        assertTrue(body.contains("Multiple lines"));
        assertFalse(body.contains("---"));
        assertFalse(body.contains("id: test"));
    }

    @Test
    void testExtractBody_NoFrontmatter() {
        String content = "Just body content\nNo frontmatter";
        String body = FrontmatterParser.extractBody(content);
        assertEquals(content, body);
    }

    @Test
    void testParseTags() {
        Map<String, Object> frontmatter = FrontmatterParser.parseContent("""
            ---
            tags: java, spring, security
            ---
            Content
            """);

        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) frontmatter.get("tags");
        assertNotNull(tags);
        assertEquals(3, tags.size());
        assertTrue(tags.contains("java"));
        assertTrue(tags.contains("spring"));
        assertTrue(tags.contains("security"));
    }

    @Test
    void testParseEmptyContent() {
        Map<String, Object> frontmatter = FrontmatterParser.parseContent("");
        assertTrue(frontmatter.isEmpty());

        frontmatter = FrontmatterParser.parseContent(null);
        assertTrue(frontmatter.isEmpty());
    }

    @Test
    void testParseEntry_EmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.md");
        Files.writeString(file, "");

        MemoryEntry entry = FrontmatterParser.parseEntry(file);
        assertNotNull(entry);
        assertEquals("", entry.getContent());
    }
}
