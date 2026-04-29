package com.example.agent.tools.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * WriteFileTool 的参数类
 */
public class WriteFileParams extends BaseToolParams {
    
    @NotNull(message = "不能为 null")
    @NotBlank(message = "不能为空")
    private String path;
    
    @NotNull(message = "不能为 null")
    private String content;
    
    public WriteFileParams() {
    }
    
    public WriteFileParams(String path, String content) {
        this.path = path;
        this.content = content;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
}
