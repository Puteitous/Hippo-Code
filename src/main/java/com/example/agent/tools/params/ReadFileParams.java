package com.example.agent.tools.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * ReadFileTool 的参数类
 */
public class ReadFileParams extends BaseToolParams {
    
    @NotNull(message = "不能为 null")
    @NotBlank(message = "不能为空")
    private String path;
    
    @Positive(message = "必须是正数")
    private Integer maxTokens = 4000;
    
    public ReadFileParams() {
    }
    
    public ReadFileParams(String path) {
        this.path = path;
    }
    
    public ReadFileParams(String path, Integer maxTokens) {
        this.path = path;
        this.maxTokens = maxTokens;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public Integer getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
