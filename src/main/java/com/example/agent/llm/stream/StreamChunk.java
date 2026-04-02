package com.example.agent.llm.stream;

import java.util.List;

/* 
 * 流式输出的分块，包含文本内容、工具调用、完成原因等信息
 */

public class StreamChunk {

    private String content;
    private List<ToolCallDelta> toolCallDeltas;
    private String finishReason;
    private boolean isToolCall;

    public StreamChunk() {
    }

    public StreamChunk(String content) {
        this.content = content;
        this.isToolCall = false;
    }

    public StreamChunk(List<ToolCallDelta> toolCallDeltas) {
        this.toolCallDeltas = toolCallDeltas;
        this.isToolCall = true;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCallDelta> getToolCallDeltas() {
        return toolCallDeltas;
    }

    public void setToolCallDeltas(List<ToolCallDelta> toolCallDeltas) {
        this.toolCallDeltas = toolCallDeltas;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public boolean isToolCall() {
        return isToolCall;
    }

    public void setToolCall(boolean toolCall) {
        isToolCall = toolCall;
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCallDeltas != null && !toolCallDeltas.isEmpty();
    }

    @Override
    public String toString() {
        return "StreamChunk{" +
                "content='" + content + '\'' +
                ", toolCallDeltas=" + toolCallDeltas +
                ", finishReason='" + finishReason + '\'' +
                ", isToolCall=" + isToolCall +
                '}';
    }
}
