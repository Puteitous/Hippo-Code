package com.example.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 用户发送消息时附带的结构化引用。
 * 由前端从输入框的引用卡片提取，后端据此读取文件内容注入 LLM 上下文。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Ref {

    /** ref 类型：file（文件引用）或 text（纯文本引用） */
    private String type;

    /** 文件路径（type=file 时必填） */
    private String path;

    /** 起始行号 */
    private Integer startLine;

    /** 结束行号 */
    private Integer endLine;

    /** 纯文本内容（type=text 时有效） */
    private String text;

    public Ref() {
    }

    public Ref(String type, String path, Integer startLine, Integer endLine, String text) {
        this.type = type;
        this.path = path;
        this.startLine = startLine;
        this.endLine = endLine;
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public void setEndLine(Integer endLine) {
        this.endLine = endLine;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
