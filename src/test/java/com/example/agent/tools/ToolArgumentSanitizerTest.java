package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolArgumentSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixJsonArguments_shouldReturnSameForNonEditTools() {
        String json = "{\"path\":\"test.txt\"}";
        String result = ToolArgumentSanitizer.fixJsonArguments("read_file", json);
        assertSame(json, result);
    }

    @Test
    void fixJsonArguments_shouldReturnSameForValidJson() throws Exception {
        String validJson = "{\"path\":\"test.txt\",\"old_text\":\"hello\",\"new_text\":\"world\"}";
        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", validJson);
        
        assertEquals(validJson, result);
        assertTrue(ToolArgumentSanitizer.requiresFixing("edit_file"));
        assertTrue(ToolArgumentSanitizer.requiresFixing("write_file"));
        
        objectMapper.readTree(result);
    }

    @Test
    void fixJsonArguments_shouldFixUnescapedQuotesInNewText() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.txt\",\n" +
            "  \"old_text\": \"hello\",\n" +
            "  \"new_text\": \"He said \"hello\" to me\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        assertEquals("He said \"hello\" to me", node.get("new_text").asText());
    }

    @Test
    void fixJsonArguments_shouldFixMultiLineContent() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.md\",\n" +
            "  \"content\": \"# Title\n" +
            "\n" +
            "Line 1\n" +
            "Line 2\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("write_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        String content = node.get("content").asText();
        assertTrue(content.contains("# Title"));
        assertTrue(content.contains("Line 1"));
        assertTrue(content.contains("Line 2"));
    }

    @Test
    void fixJsonArguments_shouldFixBothOldAndNewText() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.txt\",\n" +
            "  \"old_text\": \"Original \"text\" with quotes\",\n" +
            "  \"new_text\": \"Replacement \"text\" with more quotes\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        assertEquals("Original \"text\" with quotes", node.get("old_text").asText());
        assertEquals("Replacement \"text\" with more quotes", node.get("new_text").asText());
    }

    @Test
    void fixJsonArguments_shouldHandleContentWithBraces() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.java\",\n" +
            "  \"new_text\": \"public class Test {\n" +
            "    public void method() {\n" +
            "        System.out.println(\"test\");\n" +
            "    }\n" +
            "}\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        String newText = node.get("new_text").asText();
        assertTrue(newText.contains("public class Test"));
        assertTrue(newText.contains("public void method()"));
        assertTrue(newText.contains("System.out.println(\"test\")"));
    }

    @Test
    void fixJsonArguments_shouldHandleTabAndCarriageReturn() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.txt\",\n" +
            "  \"content\": \"Column1\tColumn2\r\nLine2\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("write_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        String content = node.get("content").asText();
        assertTrue(content.contains("\t"));
        assertTrue(content.contains("\r"));
    }

    @Test
    void fixJsonArguments_shouldHandleBackslashInContent() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.txt\",\n" +
            "  \"new_text\": \"C:\\Users\\test\\file.txt\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        assertTrue(node.get("new_text").asText().contains("Users"));
    }

    @Test
    void fixJsonArguments_shouldHandleNullAndEmpty() {
        assertNull(ToolArgumentSanitizer.fixJsonArguments("edit_file", null));
        assertEquals("", ToolArgumentSanitizer.fixJsonArguments("edit_file", ""));
    }

    @Test
    void fixJsonArguments_shouldHandleMissingFieldGracefully() {
        String json = "{\"path\":\"test.txt\"}";
        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", json);
        assertEquals(json, result);
    }

    @Test
    void fixJsonArguments_largeContentWithManyQuotes() throws Exception {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("Line ").append(i).append(": \"quoted\" content\n");
        }
        
        String brokenJson = "{\n" +
            "  \"path\": \"large-file.txt\",\n" +
            "  \"content\": \"" + largeContent + "\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("write_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        assertEquals(largeContent.toString(), node.get("content").asText());
    }

    @Test
    void fixJsonArguments_contentWithCommaAndClosingBraceInside() throws Exception {
        String brokenJson = "{\n" +
            "  \"path\": \"test.json\",\n" +
            "  \"content\": \"{\\\"key\\\": \\\"value\\\", \\\"key2\\\": \\\"value2\\\"}\"\n" +
            "}";

        String result = ToolArgumentSanitizer.fixJsonArguments("write_file", brokenJson);
        
        JsonNode node = objectMapper.readTree(result);
        String content = node.get("content").asText();
        assertTrue(content.contains("key"));
        assertTrue(content.contains("value"));
    }

    @Test
    void fixJsonArguments_shouldHandleWhitespaceAfterColon() throws Exception {
        String jsonWithSpace = "{ \"path\": \"test.txt\", \"new_text\" : \"content with \"quote\"\" }";
        
        String result = ToolArgumentSanitizer.fixJsonArguments("edit_file", jsonWithSpace);
        
        JsonNode node = objectMapper.readTree(result);
        assertEquals("content with \"quote\"", node.get("new_text").asText());
    }
}
