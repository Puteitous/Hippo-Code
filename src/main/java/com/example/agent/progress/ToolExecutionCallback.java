package com.example.agent.progress;

import com.example.agent.llm.model.ToolCall;
import com.example.agent.tools.concurrent.ToolExecutionResult;

public interface ToolExecutionCallback {

    void onToolStart(ToolCall toolCall, int index, int total);

    void onToolComplete(ToolCall toolCall, ToolExecutionResult result, int index, int total);
}
