package com.example.agent.tools;

import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.example.agent.tools.normalizer.BashNormalizer;
import com.example.agent.tools.normalizer.ReadFileNormalizer;
import com.example.agent.tools.validator.BashToolValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 2 工具特定优化测试
 */
class Phase2ToolPipelineTest {
    
    @Mock
    private ToolExecutor mockToolExecutor;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testReadFileWithNormalizer() throws Exception {
        // Arrange
        ReadFileNormalizer normalizer = new ReadFileNormalizer();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer);
        
        String toolCallId = "test-read-1";
        String arguments = "{\"path\": \" test.txt \", \"max_tokens\": 5000}";
        String expectedResult = "File content here";
        
        when(mockToolExecutor.getName()).thenReturn("read_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
        
        // 验证规范化后的参数
        verify(mockToolExecutor).execute(argThat(node -> {
            assertEquals("test.txt", node.get("path").asText());
            assertEquals(5000, node.get("max_tokens").asInt());
            return true;
        }));
    }
    
    @Test
    void testReadFileWithDefaultMaxTokens() throws Exception {
        // Arrange
        ReadFileNormalizer normalizer = new ReadFileNormalizer();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer);
        
        String toolCallId = "test-read-2";
        String arguments = "{\"path\": \"test.txt\"}";
        String expectedResult = "File content";
        
        when(mockToolExecutor.getName()).thenReturn("read_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        
        // 验证使用了默认的 max_tokens
        verify(mockToolExecutor).execute(argThat(node -> {
            assertEquals(4000, node.get("max_tokens").asInt());
            return true;
        }));
    }
    
    @Test
    void testBashWithValidator() throws Exception {
        // Arrange
        BashNormalizer normalizer = new BashNormalizer();
        BashToolValidator validator = new BashToolValidator();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer, validator);
        
        String toolCallId = "test-bash-1";
        String arguments = "{\"command\": \"git status\"}";
        String expectedResult = "On branch main";
        
        when(mockToolExecutor.getName()).thenReturn("bash");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
    }
    
    @Test
    void testBashWithBlockedCommand() throws Exception {
        // Arrange
        BashNormalizer normalizer = new BashNormalizer();
        BashToolValidator validator = new BashToolValidator();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer, validator);
        
        String toolCallId = "test-bash-2";
        String arguments = "{\"command\": \"rm -rf /\"}";
        
        when(mockToolExecutor.getName()).thenReturn("bash");
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("被禁止") || result.getErrorMessage().contains("危险模式"));
        
        // 验证工具没有被执行
        verify(mockToolExecutor, never()).execute(any(JsonNode.class));
    }
    
    @Test
    void testBashWithNotAllowedCommand() throws Exception {
        // Arrange
        BashNormalizer normalizer = new BashNormalizer();
        BashToolValidator validator = new BashToolValidator();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer, validator);
        
        String toolCallId = "test-bash-3";
        String arguments = "{\"command\": \"python script.py\"}";
        
        when(mockToolExecutor.getName()).thenReturn("bash");
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("不在白名单中"));
        
        verify(mockToolExecutor, never()).execute(any(JsonNode.class));
    }
    
    @Test
    void testBashWithNormalization() throws Exception {
        // Arrange
        BashNormalizer normalizer = new BashNormalizer();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer);
        
        String toolCallId = "test-bash-4";
        String arguments = "{\"command\": \"  git status  \", \"working_dir\": \"src\\\\test\"}";
        String expectedResult = "On branch main";
        
        when(mockToolExecutor.getName()).thenReturn("bash");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, arguments, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        
        // 验证规范化后的参数
        verify(mockToolExecutor).execute(argThat(node -> {
            assertEquals("git status", node.get("command").asText());
            assertEquals("src/test", node.get("working_dir").asText());
            assertEquals(30, node.get("timeout").asInt());
            return true;
        }));
    }
    
    @Test
    void testPipelineWithAllPhases() throws Exception {
        // Arrange
        ReadFileNormalizer normalizer = new ReadFileNormalizer();
        ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor, normalizer);
        
        String toolCallId = "test-all-1";
        String brokenJson = "{'path':'test.txt'}"; // 单引号
        String expectedResult = "Content";
        
        when(mockToolExecutor.getName()).thenReturn("read_file");
        when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn(expectedResult);
        
        // Act
        ToolExecutionResult result = pipeline.execute(toolCallId, brokenJson, 0);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult, result.getResult());
        
        // 验证所有 Phase 都执行了
        verify(mockToolExecutor).execute(any(JsonNode.class));
    }
}
