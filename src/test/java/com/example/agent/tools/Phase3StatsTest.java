package com.example.agent.tools;

import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.example.agent.tools.normalizer.BashNormalizer;
import com.example.agent.tools.normalizer.ReadFileNormalizer;
import com.example.agent.tools.stats.JsonRepairStats;
import com.example.agent.tools.stats.ToolExecutionStats;
import com.example.agent.tools.validator.BashToolValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 3 统计和结构化日志测试
 */
class Phase3StatsTest {
    
    @Mock
    private ToolExecutor mockToolExecutor;
    
    private ToolExecutionStats executionStats;
    private JsonRepairStats jsonRepairStats;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executionStats = new ToolExecutionStats();
        jsonRepairStats = new JsonRepairStats();
    }
    
    @Nested
    @DisplayName("工具执行统计测试")
    class ToolExecutionStatsTests {
        
        @Test
        @DisplayName("统计成功执行")
        void testRecordSuccessfulExecution() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("File content");
            
            // Act
            ToolExecutionResult result = pipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0);
            
            // Assert
            assertTrue(result.isSuccess());
            assertEquals(1, executionStats.getTotalCalls());
            assertEquals(1, executionStats.getSuccessCalls());
            assertEquals(0, executionStats.getFailureCalls());
            assertEquals(1, executionStats.getToolCallCount("read_file"));
        }
        
        @Test
        @DisplayName("统计失败执行")
        void testRecordFailedExecution() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenThrow(new RuntimeException("File not found"));
            
            // Act
            ToolExecutionResult result = pipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0);
            
            // Assert
            assertFalse(result.isSuccess());
            assertEquals(1, executionStats.getTotalCalls());
            assertEquals(0, executionStats.getSuccessCalls());
            assertEquals(1, executionStats.getFailureCalls());
        }
        
        @Test
        @DisplayName("统计多次执行")
        void testRecordMultipleExecutions() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            // Act
            pipeline.execute("call-1", "{\"path\": \"test1.txt\"}", 0);
            pipeline.execute("call-2", "{\"path\": \"test2.txt\"}", 1);
            pipeline.execute("call-3", "{\"path\": \"test3.txt\"}", 2);
            
            // Assert
            assertEquals(3, executionStats.getTotalCalls());
            assertEquals(3, executionStats.getSuccessCalls());
            assertEquals(0, executionStats.getFailureCalls());
            assertEquals(3, executionStats.getToolCallCount("read_file"));
            assertTrue(executionStats.getTotalExecutionTimeMs() >= 0);
        }
        
        @Test
        @DisplayName("统计不同工具")
        void testRecordDifferentTools() throws Exception {
            // Arrange
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Result");
            
            // Act - read_file
            when(mockToolExecutor.getName()).thenReturn("read_file");
            ToolExecutionPipeline readPipeline = new ToolExecutionPipeline(
                    mockToolExecutor, null, null, executionStats, jsonRepairStats);
            readPipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0);
            
            // Act - write_file
            when(mockToolExecutor.getName()).thenReturn("write_file");
            ToolExecutionPipeline writePipeline = new ToolExecutionPipeline(
                    mockToolExecutor, null, null, executionStats, jsonRepairStats);
            writePipeline.execute("call-2", "{\"path\": \"test.txt\", \"content\": \"hello\"}", 1);
            
            // Assert
            assertEquals(2, executionStats.getTotalCalls());
            assertEquals(1, executionStats.getToolCallCount("read_file"));
            assertEquals(1, executionStats.getToolCallCount("write_file"));
        }
        
        @Test
        @DisplayName("获取统计摘要")
        void testGetSummary() throws Exception {
            // Arrange
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            // Act
            pipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0);
            pipeline.execute("call-2", "{\"path\": \"test.txt\"}", 1);
            
            String summary = executionStats.getSummary();
            
            // Assert
            assertNotNull(summary);
            assertTrue(summary.contains("总调用次数: 2"));
            assertTrue(summary.contains("成功: 2"));
            assertTrue(summary.contains("失败: 0"));
        }
    }
    
    @Nested
    @DisplayName("JSON 修复统计测试")
    class JsonRepairStatsTests {
        
        @Test
        @DisplayName("统计 JSON 修复成功")
        void testRecordJsonRepair() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            // Act - 使用单引号的 JSON (需要修复)
            ToolExecutionResult result = pipeline.execute("call-1", "{'path': 'test.txt'}", 0);
            
            // Assert - 优雅降级：修复成功则执行成功
            assertTrue(result.isSuccess());
            assertTrue(jsonRepairStats.getTotalRepairs() >= 1);
            assertEquals(1, jsonRepairStats.getRepairCountByTool("read_file"));
        }
        
        @Test
        @DisplayName("JSON 修复失败时优雅降级")
        void testJsonRepairGracefulDegradation() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            // Act - 使用无法修复的 JSON，应该降级为空对象继续执行
            ToolExecutionResult result = pipeline.execute("call-1", "{invalid json}", 0);
            
            // Assert - 优雅降级：即使 JSON 解析失败，也会降级为空对象继续执行
            // ReadFileNormalizer 会给空对象添加默认的 max_tokens，所以验证会通过
            // 工具会被执行并返回成功
            assertTrue(result.isSuccess());
            assertTrue(jsonRepairStats.getTotalAttempts() >= 1);
            assertTrue(jsonRepairStats.getTotalFailures() >= 1);
        }
        
        @Test
        @DisplayName("统计 JSON 解析失败")
        void testRecordJsonFailure() throws Exception {
            // Arrange
            BashNormalizer normalizer = new BashNormalizer();
            BashToolValidator validator = new BashToolValidator();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, validator, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("bash");
            
            // Act - 使用无法修复的 JSON，优雅降级为空对象后继续执行
            ToolExecutionResult result = pipeline.execute("call-1", "{invalid json}", 0);
            
            // Assert - 优雅降级：空对象缺少 command 参数，验证会返回友好错误
            assertFalse(result.isSuccess());
            assertTrue(jsonRepairStats.getTotalAttempts() >= 1);
            // 验证错误应该包含友好提示信息
            assertNotNull(result.getErrorMessage());
        }
        
        @Test
        @DisplayName("统计 JSON 修复率")
        void testJsonRepairRate() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            // Act
            pipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0); // 正常 JSON
            pipeline.execute("call-2", "{'path': 'test.txt'}", 1); // 需要修复
            
            // Assert
            assertTrue(jsonRepairStats.getTotalAttempts() >= 2);
            double repairRate = jsonRepairStats.getRepairRate();
            assertTrue(repairRate >= 0.0 && repairRate <= 100.0);
        }
        
        @Test
        @DisplayName("获取 JSON 修复详细摘要")
        void testGetDetailedSummary() throws Exception {
            // Arrange
            ReadFileNormalizer normalizer = new ReadFileNormalizer();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, null, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            // Act
            pipeline.execute("call-1", "{'path': 'test.txt'}", 0);
            
            String summary = jsonRepairStats.getDetailedSummary();
            
            // Assert
            assertNotNull(summary);
            assertTrue(summary.contains("JSON 修复统计"));
        }
    }
    
    @Nested
    @DisplayName("验证错误统计测试")
    class ValidationErrorStatsTests {
        
        @Test
        @DisplayName("统计验证错误")
        void testRecordValidationError() throws Exception {
            // Arrange
            BashNormalizer normalizer = new BashNormalizer();
            BashToolValidator validator = new BashToolValidator();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, validator, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("bash");
            
            // Act - 使用危险的 bash 命令
            ToolExecutionResult result = pipeline.execute("call-1", "{\"command\": \"rm -rf /\"}", 0);
            
            // Assert
            assertFalse(result.isSuccess());
            assertTrue(executionStats.getTotalValidationErrors() >= 1);
        }
        
        @Test
        @DisplayName("统计空参数验证错误")
        void testRecordEmptyParameterError() throws Exception {
            // Arrange
            BashNormalizer normalizer = new BashNormalizer();
            BashToolValidator validator = new BashToolValidator();
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, normalizer, validator, executionStats, jsonRepairStats);
            
            when(mockToolExecutor.getName()).thenReturn("bash");
            
            // Act - 使用不在白名单中的命令
            ToolExecutionResult result = pipeline.execute("call-1", "{\"command\": \"python script.py\"}", 0);
            
            // Assert
            assertFalse(result.isSuccess());
            assertTrue(executionStats.getTotalValidationErrors() >= 1);
        }
    }
    
    @Nested
    @DisplayName("统计对象获取测试")
    class StatsGetterTests {
        
        @Test
        @DisplayName("获取执行统计对象")
        void testGetExecutionStats() throws Exception {
            // Arrange
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, null, null, executionStats, jsonRepairStats);
            
            // Act
            pipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0);
            
            // Assert
            assertNotNull(pipeline.getExecutionStats());
            assertEquals(1, pipeline.getExecutionStats().getTotalCalls());
        }
        
        @Test
        @DisplayName("获取 JSON 修复统计对象")
        void testGetJsonRepairStats() throws Exception {
            // Arrange
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(
                    mockToolExecutor, null, null, executionStats, jsonRepairStats);
            
            // Act
            pipeline.execute("call-1", "{'path': 'test.txt'}", 0);
            
            // Assert
            assertNotNull(pipeline.getJsonRepairStats());
            assertTrue(pipeline.getJsonRepairStats().getTotalRepairs() >= 1);
        }
        
        @Test
        @DisplayName("无统计对象时不抛出异常")
        void testNoStatsNoException() throws Exception {
            // Arrange
            when(mockToolExecutor.getName()).thenReturn("read_file");
            when(mockToolExecutor.execute(any(JsonNode.class))).thenReturn("Content");
            
            // Act - 不传入统计对象
            ToolExecutionPipeline pipeline = new ToolExecutionPipeline(mockToolExecutor);
            ToolExecutionResult result = pipeline.execute("call-1", "{\"path\": \"test.txt\"}", 0);
            
            // Assert
            assertTrue(result.isSuccess());
            assertNull(pipeline.getExecutionStats());
            assertNull(pipeline.getJsonRepairStats());
        }
    }
}
