package com.example.agent.testutil;

import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public final class TestFixtures {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestFixtures() {
    }

    public static class Messages {
        public static Message systemMessage(String content) {
            return Message.system(content);
        }

        public static Message userMessage(String content) {
            return Message.user(content);
        }

        public static Message assistantMessage(String content) {
            return Message.assistant(content);
        }

        public static Message toolResultMessage(String toolCallId, String toolName, String result) {
            return Message.toolResult(toolCallId, toolName, result);
        }

        public static List<Message> simpleConversation(String systemPrompt, String userMessage) {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(systemPrompt));
            messages.add(Message.user(userMessage));
            return messages;
        }
    }

    public static class ToolCalls {
        public static ToolCall bashToolCall(String command) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-bash-" + System.nanoTime());
            toolCall.setFunction(new FunctionCall("bash", 
                    String.format("{\"command\": \"%s\"}", command)));
            return toolCall;
        }

        public static ToolCall readFileToolCall(String filePath) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-read-" + System.nanoTime());
            toolCall.setFunction(new FunctionCall("read_file", 
                    String.format("{\"path\": \"%s\"}", filePath)));
            return toolCall;
        }

        public static ToolCall writeFileToolCall(String filePath, String content) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-write-" + System.nanoTime());
            ObjectNode args = OBJECT_MAPPER.createObjectNode();
            args.put("path", filePath);
            args.put("content", content);
            try {
                toolCall.setFunction(new FunctionCall("write_file", 
                        OBJECT_MAPPER.writeValueAsString(args)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return toolCall;
        }

        public static ToolCall grepToolCall(String pattern) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-grep-" + System.nanoTime());
            toolCall.setFunction(new FunctionCall("grep", 
                    String.format("{\"pattern\": \"%s\"}", pattern)));
            return toolCall;
        }

        public static ToolCall askUserToolCall(String question) {
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call-ask-" + System.nanoTime());
            ObjectNode args = OBJECT_MAPPER.createObjectNode();
            args.put("question", question);
            try {
                toolCall.setFunction(new FunctionCall("ask_user", 
                        OBJECT_MAPPER.writeValueAsString(args)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return toolCall;
        }

        public static List<ToolCall> multipleToolCalls(ToolCall... calls) {
            return List.of(calls);
        }
    }

    public static class Responses {
        public static ChatResponse textResponse(String content) {
            return LlmResponseBuilder.create()
                    .content(content)
                    .build();
        }

        public static ChatResponse toolCallResponse(ToolCall... toolCalls) {
            LlmResponseBuilder builder = LlmResponseBuilder.create()
                    .finishReasonToolCalls();
            for (ToolCall tc : toolCalls) {
                builder.addToolCall(tc);
            }
            return builder.build();
        }

        public static ChatResponse emptyResponse() {
            return LlmResponseBuilder.empty();
        }
    }

    public static class Strings {
        public static String sampleCode() {
            return """
                public class Sample {
                    public void method() {
                        System.out.println("Hello");
                    }
                }
                """;
        }

        public static String sampleJson() {
            return """
                {"name": "test", "value": 123}
                """;
        }

        public static String sampleError() {
            return "java.lang.NullPointerException: Cannot invoke method on null object";
        }

        public static String repeat(String s, int times) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(s);
            }
            return sb.toString();
        }
    }
}
