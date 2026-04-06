package com.example.agent.llm.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SseParserTest {

    private SseParser parser;

    @BeforeEach
    void setUp() {
        parser = new SseParser();
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("null输入返回null")
        void testNullInput() {
            assertNull(parser.parse(null));
        }

        @Test
        @DisplayName("空字符串返回null")
        void testEmptyString() {
            assertNull(parser.parse(""));
        }

        @Test
        @DisplayName("空白字符串返回null")
        void testWhitespaceString() {
            assertNull(parser.parse("   "));
            assertNull(parser.parse("\t\n"));
        }

        @Test
        @DisplayName("非data前缀返回null")
        void testNonDataPrefix() {
            assertNull(parser.parse("not data: content"));
            assertNull(parser.parse("error: something"));
        }

        @Test
        @DisplayName("[DONE]标记返回null")
        void testDoneMarker() {
            assertNull(parser.parse("data: [DONE]"));
        }

        @Test
        @DisplayName("无效JSON返回null")
        void testInvalidJson() {
            assertNull(parser.parse("data: not valid json"));
            assertNull(parser.parse("data: {broken json"));
        }
    }

    @Nested
    @DisplayName("内容解析测试")
    class ContentParsingTests {

        @Test
        @DisplayName("解析简单文本内容")
        void testSimpleContent() {
            String line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals("Hello", chunk.getContent());
            assertFalse(chunk.isToolCall());
        }

        @Test
        @DisplayName("解析空内容")
        void testEmptyContent() {
            String line = "data: {\"choices\":[{\"delta\":{}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertNull(chunk.getContent());
            assertFalse(chunk.hasContent());
        }

        @Test
        @DisplayName("解析特殊字符内容")
        void testSpecialCharacters() {
            String line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\\nWorld\\t!\\\"quote\\\"\"}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertTrue(chunk.getContent().contains("Hello"));
        }

        @Test
        @DisplayName("解析Unicode内容")
        void testUnicodeContent() {
            String line = "data: {\"choices\":[{\"delta\":{\"content\":\"你好世界\"}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals("你好世界", chunk.getContent());
        }
    }

    @Nested
    @DisplayName("工具调用解析测试")
    class ToolCallParsingTests {

        @Test
        @DisplayName("解析工具调用")
        void testToolCall() {
            String line = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-123\",\"type\":\"function\",\"function\":{\"name\":\"bash\"}}]}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertTrue(chunk.isToolCall());
            assertTrue(chunk.hasToolCalls());
            assertEquals(1, chunk.getToolCallDeltas().size());
            
            ToolCallDelta delta = chunk.getToolCallDeltas().get(0);
            assertEquals(0, delta.getIndex());
            assertEquals("call-123", delta.getId());
            assertEquals("function", delta.getType());
            assertEquals("bash", delta.getFunction().getName());
        }

        @Test
        @DisplayName("解析工具调用参数增量")
        void testToolCallArguments() {
            String line = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"command\\\":\\\"ls\\\"}\"}}]}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertTrue(chunk.hasToolCalls());
            
            ToolCallDelta delta = chunk.getToolCallDeltas().get(0);
            assertEquals("{\"command\":\"ls\"}", delta.getFunction().getArguments());
        }

        @Test
        @DisplayName("解析多个工具调用")
        void testMultipleToolCalls() {
            String line = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\"},{\"index\":1,\"id\":\"call-2\"}]}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals(2, chunk.getToolCallDeltas().size());
            assertEquals(0, chunk.getToolCallDeltas().get(0).getIndex());
            assertEquals(1, chunk.getToolCallDeltas().get(1).getIndex());
        }

        @Test
        @DisplayName("工具调用index超出范围时跳过")
        void testToolCallIndexOutOfRange() {
            String line = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":2000,\"id\":\"call-1\"}]}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertTrue(chunk.hasToolCalls());
            assertNull(chunk.getToolCallDeltas().get(0).getIndex());
        }

        @Test
        @DisplayName("空工具调用名称被跳过")
        void testEmptyToolCallName() {
            String line = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"\"}}]}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertTrue(chunk.hasToolCalls());
            assertNull(chunk.getToolCallDeltas().get(0).getFunction().getName());
        }
    }

    @Nested
    @DisplayName("finishReason解析测试")
    class FinishReasonTests {

        @Test
        @DisplayName("解析stop原因")
        void testStopFinishReason() {
            String line = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals("stop", chunk.getFinishReason());
        }

        @Test
        @DisplayName("解析tool_calls原因")
        void testToolCallsFinishReason() {
            String line = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals("tool_calls", chunk.getFinishReason());
        }

        @Test
        @DisplayName("null finishReason")
        void testNullFinishReason() {
            String line = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":null}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertNull(chunk.getFinishReason());
        }
    }

    @Nested
    @DisplayName("Usage解析测试")
    class UsageParsingTests {

        @Test
        @DisplayName("解析usage信息")
        void testUsage() {
            String line = "data: {\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50,\"total_tokens\":150},\"choices\":[{\"delta\":{}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertTrue(chunk.hasUsage());
            assertEquals(100, chunk.getUsage().getPromptTokens());
            assertEquals(50, chunk.getUsage().getCompletionTokens());
            assertEquals(150, chunk.getUsage().getTotalTokens());
        }

        @Test
        @DisplayName("无usage信息")
        void testNoUsage() {
            String line = "data: {\"choices\":[{\"delta\":{\"content\":\"test\"}}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertFalse(chunk.hasUsage());
        }
    }

    @Nested
    @DisplayName("isDone测试")
    class IsDoneTests {

        @Test
        @DisplayName("检测[DONE]标记")
        void testIsDoneTrue() {
            assertTrue(parser.isDone("data: [DONE]"));
            assertTrue(parser.isDone("data:  [DONE]  "));
        }

        @Test
        @DisplayName("非[DONE]标记返回false")
        void testIsDoneFalse() {
            assertFalse(parser.isDone("data: {\"content\":\"test\"}"));
            assertFalse(parser.isDone("not data: [DONE]"));
            assertFalse(parser.isDone(null));
            assertFalse(parser.isDone(""));
        }
    }

    @Nested
    @DisplayName("空choices测试")
    class EmptyChoicesTests {

        @Test
        @DisplayName("空choices数组")
        void testEmptyChoices() {
            String line = "data: {\"choices\":[]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertFalse(chunk.hasContent());
            assertFalse(chunk.hasToolCalls());
        }

        @Test
        @DisplayName("无choices字段")
        void testNoChoices() {
            String line = "data: {\"id\":\"test\"}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertFalse(chunk.hasContent());
        }

        @Test
        @DisplayName("null choices")
        void testNullChoices() {
            String line = "data: {\"choices\":null}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertFalse(chunk.hasContent());
        }
    }

    @Nested
    @DisplayName("复杂场景测试")
    class ComplexScenarioTests {

        @Test
        @DisplayName("同时包含内容和工具调用")
        void testContentAndToolCalls() {
            String line = "data: {\"choices\":[{\"delta\":{\"content\":\"text\",\"tool_calls\":[{\"index\":0}]},\"finish_reason\":\"tool_calls\"}]}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals("text", chunk.getContent());
            assertTrue(chunk.hasToolCalls());
            assertEquals("tool_calls", chunk.getFinishReason());
        }

        @Test
        @DisplayName("完整响应块")
        void testCompleteChunk() {
            String line = "data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1234567890,\"model\":\"gpt-4\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
            
            StreamChunk chunk = parser.parse(line);
            
            assertNotNull(chunk);
            assertEquals("Hello", chunk.getContent());
            assertTrue(chunk.hasUsage());
        }
    }
}
