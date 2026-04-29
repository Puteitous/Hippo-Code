package com.example.agent.tools;

import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ToolExecutionPipeline 单元测试
 */
class ToolExecutionPipelineTest {
    
    @Mock
    private ToolExecutor mockToolExecutor;
    
    private ToolExecutionPipeline pipeline;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pipeline = new ToolExecutionPipeline(mockToolExecutor);
    }
    
    @Test
    void testExecuteWithValidJson() throws Exception {
        // Arrange
        String toolCallId = "test-call-1";
        String arguments = "{\"path\": \"test.txt\", \"content\": \"hello\"}";
        String expectedResult = "File written successfully";
        
        when(mockToolExecutor.getName()).thenReturn("write_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
        assertEquals(toolCallId, result.getToolCallId());
        assertEquals("write_file", result.getToolName());
        
        verify(mockToolExecutor).execute(any(JsonNode.class));
    }
    
    @Test
    void testExecuteWithBrokenJson() throws Exception {
        // Arrange
        String toolCallId = "test-call-2";
        String brokenJson = "{'path': 'test.txt', 'content': 'hello'}"; // 单引号
        String expectedResult = "File written successfully";
        
        when(mockToolExecutor.getName()).thenReturn("write_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, brokenJson, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
        
        verify(mockToolExecutor).execute(any(JsonNode.class));
    }
    
    @Test
    void testExecuteWithInvalidJson() throws Exception {
        // Arrange
        String toolCallId = "test-call-3";
        String invalidJson = "{invalid json}";
        
        when(mockToolExecutor.getName()).thenReturn("write_file");
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, invalidJson, 0);
        
        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("JSON 参数格式错误"));
        
        verify(mockToolExecutor, never()).execute(any(JsonNode.class));
    }
    
    @Test
    void testExecuteWithChineseCharacters() throws Exception {
        // Arrange
        String toolCallId = "test-call-4";
        String arguments = "{\"old_text\": \"旧内容\", \"new_text\": \"新内容\"}";
        String expectedResult = "File edited successfully";
        
        when(mockToolExecutor.getName()).thenReturn("edit_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
        
        verify(mockToolExecutor).execute(any(JsonNode.class));
    }
    
    @Test
    void testExecuteWithMissingCommas() throws Exception {
        // Arrange
        String toolCallId = "test-call-5";
        String brokenJson = "{\"path\": \"test.txt\" \"content\": \"hello\"}"; // 缺少逗号
        String expectedResult = "File written successfully";
        
        when(mockToolExecutor.getName()).thenReturn("write_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, brokenJson, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
        
        verify(mockToolExecutor).execute(any(JsonNode.class));
    }
    
    @Test
    void testExecuteWithExecutionTime() throws Exception {
        // Arrange
        String toolCallId = "test-call-6";
        String arguments = "{\"path\": \"test.txt\"}";
        String expectedResult = "Success";
        
        when(mockToolExecutor.getName()).thenReturn("read_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getExecutionTimeMs() >= 0);
        
        verify(mockToolExecutor).execute(any(JsonNode.class));
    }
}
