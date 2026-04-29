package com.example.agent.tools.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * EditFileTool 的参数类
 */
public class EditFileParams extends BaseToolParams {
    
    @NotNull(message = "不能为 null")
    @NotBlank(message = "不能为空")
    private String path;
    
    @NotNull(message = "不能为 null")
    @NotBlank(message = "不能为空")
    private String oldText;
    
    @NotNull(message = "不能为 null")
    @NotBlank(message = "不能为空")
    private String newText;
    
    public EditFileParams() {
    }
    
    public EditFileParams(String path, String oldText, String newText) {
        this.path = path;
        this.oldText = oldText;
        this.newText = newText;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getOldText() {
        return oldText;
    }
    
    public void setOldText(String oldText) {
        this.oldText = oldText;
    }
    
    public String getNewText() {
        return newText;
    }
    
    public void setNewText(String newText) {
        this.newText = newText;
    }
}
