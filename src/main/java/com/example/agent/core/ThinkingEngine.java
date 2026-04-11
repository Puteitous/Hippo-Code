package com.example.agent.core;

import com.example.agent.context.index.CodeIndex;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.concurrent.ConcurrentToolExecutor;
import com.example.agent.tools.concurrent.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ThinkingEngine {

    private static final Logger logger = LoggerFactory.getLogger(ThinkingEngine.class);
    private static final int DEFAULT_RELEVANT_FILES = 3;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConcurrentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private CodeIndex codeIndex;
    private boolean memoryBoostEnabled = true;

    public ThinkingEngine(LlmClient llmClient, ToolRegistry toolRegistry, ConcurrentToolExecutor toolExecutor) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.objectMapper = new ObjectMapper();
    }

    public void setCodeIndex(CodeIndex codeIndex) {
        this.codeIndex = codeIndex;
    }

    public void setMemoryBoostEnabled(boolean enabled) {
        this.memoryBoostEnabled = enabled;
    }

    public <T> T think(ThinkingContext<T> context) throws LlmException {
        logger.debug("开始思考，最大轮数: {}", context.getMaxRounds());

        List<Message> messages = buildInitialMessages(context);
        int round = 0;

        while (round < context.getMaxRounds()) {
            round++;
            logger.debug("思考第 {} 轮", round);

            List<com.example.agent.llm.model.Tool> availableTools = buildAvailableTools(context);
            ChatResponse response = llmClient.chat(messages, availableTools);

            if (response == null) {
                throw new LlmException("LLM 返回空响应");
            }

            if (!response.hasToolCalls()) {
                logger.debug("第 {} 轮无工具调用，思考完成", round);
                String finalContent = response.getContent() != null ? response.getContent() : "";
                return context.parseResult(finalContent);
            }

            List<ToolCall> allowedCalls = filterAllowedTools(response.getToolCalls(), context);

            if (allowedCalls.isEmpty()) {
                logger.debug("无允许的工具调用，强制输出结论");
                messages.add(Message.user("工具调用被拒绝，请直接输出结论"));
                continue;
            }

            logger.debug("执行 {} 个工具调用", allowedCalls.size());
            List<ToolExecutionResult> results = toolExecutor.executeConcurrently(allowedCalls);

            for (ToolExecutionResult result : results) {
                String toolResult = result.isSuccess()
                        ? result.getResult()
                        : "Error: " + result.getErrorMessage();
                messages.add(Message.toolResult(result.getToolCallId(), result.getToolName(), toolResult));
            }
        }

        logger.debug("达到最大轮数，强制输出结论");
        ChatResponse finalResponse = llmClient.chat(messages);
        if (finalResponse == null) {
            throw new LlmException("LLM 返回空响应");
        }
        String finalContent = finalResponse.getContent() != null ? finalResponse.getContent() : "";
        return context.parseResult(finalContent);
    }

    private <T> List<Message> buildInitialMessages(ThinkingContext<T> context) {
        List<Message> messages = new ArrayList<>();

        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isEmpty()) {
            messages.add(Message.system(context.getSystemPrompt()));
        }

        if (memoryBoostEnabled && codeIndex != null && context.getUserInput() != null) {
            List<Message> memoryContext = buildMemoryContext(context.getUserInput());
            if (!memoryContext.isEmpty()) {
                logger.debug("CodeIndex 检索到 {} 个相关文件注入上下文", memoryContext.size());
                messages.addAll(memoryContext);
            }
        }

        messages.addAll(context.getHistory());

        if (context.getUserInput() != null && !context.getUserInput().isEmpty()) {
            messages.add(Message.user(context.getUserInput()));
        }

        return messages;
    }

    private List<Message> buildMemoryContext(String userInput) {
        List<Message> memoryMessages = new ArrayList<>();

        List<String> relevantFiles = codeIndex.search(userInput, DEFAULT_RELEVANT_FILES, 1000);

        if (!relevantFiles.isEmpty()) {
            StringBuilder memoryPrompt = new StringBuilder("\n=== 代码库相关参考（来自记忆检索） ===\n");
            memoryPrompt.append("在代码库中找到以下相关文件：\n");

            for (String filePath : relevantFiles) {
                memoryPrompt.append("  - ").append(filePath).append("\n");
            }

            memoryPrompt.append("\n提示：你可以直接引用这些文件，无需再次搜索。\n");
            memoryPrompt.append("如果这些文件不够用，再调用工具继续探索。\n");

            memoryMessages.add(Message.system(memoryPrompt.toString()));
        }

        return memoryMessages;
    }

    private <T> List<com.example.agent.llm.model.Tool> buildAvailableTools(ThinkingContext<T> context) {
        return toolRegistry.getAllTools().stream()
                .filter(tool -> context.getAllowedTools() == null
                        || context.getAllowedTools().contains(tool.getName()))
                .<com.example.agent.llm.model.Tool>map(executor -> {
                    try {
                        JsonNode schema = objectMapper.readTree(executor.getParametersSchema());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parameters = objectMapper.convertValue(schema, Map.class);
                        return com.example.agent.llm.model.Tool.of(
                                executor.getName(),
                                executor.getDescription(),
                                parameters
                        );
                    } catch (Exception e) {
                        logger.warn("解析工具 schema 失败: {}", executor.getName());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private <T> List<ToolCall> filterAllowedTools(List<ToolCall> toolCalls, ThinkingContext<T> context) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return new ArrayList<>();
        }

        return toolCalls.stream()
                .filter(call -> {
                    String toolName = call.getFunction() != null ? call.getFunction().getName() : null;
                    if (toolName == null || toolName.isEmpty()) {
                        return false;
                    }
                    return context.getAllowedTools() == null
                            || context.getAllowedTools().contains(toolName);
                })
                .collect(Collectors.toList());
    }
}
