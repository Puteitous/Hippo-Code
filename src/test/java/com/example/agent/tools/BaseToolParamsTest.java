package com.example.agent.tools;

import com.example.agent.tools.params.EditFileParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseToolParams 和 EditFileParams 单元测试
 */
class BaseToolParamsTest {
    
    @Test
    void testEditFileParamsValidationSuccess() throws Exception {
        // Arrange
        EditFileParams params = new EditFileParams(
            "test.txt",
            "old content",
            "new content"
        );
        
        // Act & Assert
        assertDoesNotThrow(() -> params.validate());
        assertEquals("test.txt", params.getPath());
        assertEquals("old content", params.getOldText());
        assertEquals("new content", params.getNewText());
    }
    
    @Test
    void testEditFileParamsValidationNullPath() {
        // Arrange
        EditFileParams params = new EditFileParams();
        params.setPath(null);
        params.setOldText("old content");
        params.setNewText("new content");
        
        // Act & Assert
        ToolExecutionException exception = assertThrows(
            ToolExecutionException.class,
            () -> params.validate()
        );
        
        assertTrue(exception.getMessage().contains("参数验证失败"));
        assertTrue(exception.getMessage().contains("path"));
    }
    
    @Test
    void testEditFileParamsValidationEmptyPath() {
        // Arrange
        EditFileParams params = new EditFileParams();
        params.setPath("");
        params.setOldText("old content");
        params.setNewText("new content");
        
        // Act & Assert
        ToolExecutionException exception = assertThrows(
            ToolExecutionException.class,
            () -> params.validate()
        );
        
        assertTrue(exception.getMessage().contains("参数验证失败"));
        assertTrue(exception.getMessage().contains("path"));
    }
    
    @Test
    void testEditFileParamsValidationNullOldText() {
        // Arrange
        EditFileParams params = new EditFileParams();
        params.setPath("test.txt");
        params.setOldText(null);
        params.setNewText("new content");
        
        // Act & Assert
        ToolExecutionException exception = assertThrows(
            ToolExecutionException.class,
            () -> params.validate()
        );
        
        assertTrue(exception.getMessage().contains("参数验证失败"));
        assertTrue(exception.getMessage().contains("oldText"));
    }
    
    @Test
    void testEditFileParamsValidationNullNewText() {
        // Arrange
        EditFileParams params = new EditFileParams();
        params.setPath("test.txt");
        params.setOldText("old content");
        params.setNewText(null);
        
        // Act & Assert
        ToolExecutionException exception = assertThrows(
            ToolExecutionException.class,
            () -> params.validate()
        );
        
        assertTrue(exception.getMessage().contains("参数验证失败"));
        assertTrue(exception.getMessage().contains("newText"));
    }
    
    @Test
    void testEditFileParamsValidationAllNull() {
        // Arrange
        EditFileParams params = new EditFileParams();
        params.setPath(null);
        params.setOldText(null);
        params.setNewText(null);
        
        // Act & Assert
        ToolExecutionException exception = assertThrows(
            ToolExecutionException.class,
            () -> params.validate()
        );
        
        assertTrue(exception.getMessage().contains("参数验证失败"));
        assertTrue(exception.getMessage().contains("path"));
        assertTrue(exception.getMessage().contains("oldText"));
        assertTrue(exception.getMessage().contains("newText"));
    }
    
    @Test
    void testEditFileParamsWithChineseCharacters() throws Exception {
        // Arrange
        EditFileParams params = new EditFileParams();
        params.setPath("测试文件.txt");
        params.setOldText("旧内容");
        params.setNewText("新内容");
        
        // Act & Assert
        assertDoesNotThrow(() -> params.validate());
        assertEquals("测试文件.txt", params.getPath());
        assertEquals("旧内容", params.getOldText());
        assertEquals("新内容", params.getNewText());
    }
    
    @Test
    void testGetParamType() {
        // Arrange
        EditFileParams params = new EditFileParams();
        
        // Act & Assert
        assertEquals("EditFileParams", params.getParamType());
    }
}
