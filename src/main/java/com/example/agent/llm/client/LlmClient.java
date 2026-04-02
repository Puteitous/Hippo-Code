package com.example.agent.llm;

import java.util.List;

public interface LlmClient {

    ChatResponse chat(List<Message> messages) throws LlmException;

    ChatResponse chat(List<Message> messages, List<Tool> tools) throws LlmException;

    ChatResponse chatWithTools(List<Message> messages, List<Tool> tools) throws LlmException;

    ChatResponse executeRequest(ChatRequest request) throws LlmException;

    ChatResponse continueWithToolResult(ChatResponse previousResponse, List<Message> messages, 
                                        String toolCallId, String toolName, String toolResult) throws LlmException;

    ChatResponse continueWithToolResults(ChatResponse previousResponse, List<Message> messages, 
                                         List<ToolResult> toolResults) throws LlmException;

    class ToolResult {
        private final String toolCallId;
        private final String toolName;
        private final String result;

        public ToolResult(String toolCallId, String toolName, String result) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.result = result;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public String getToolName() {
            return toolName;
        }

        public String getResult() {
            return result;
        }
    }
}
