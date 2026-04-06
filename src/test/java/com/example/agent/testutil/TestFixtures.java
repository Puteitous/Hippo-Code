package com.example.agent.testutil;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.plan.ExecutionContext;
import com.example.agent.plan.ExecutionPlan;
import com.example.agent.plan.ExecutionStep;
import com.example.agent.plan.ExecutionStrategy;
import com.example.agent.plan.PlanningContext;
import com.example.agent.plan.StepType;
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

    public static class Intents {
        public static IntentResult simpleQuestion(String userInput) {
            return IntentResult.builder()
                    .type(IntentType.QUESTION)
                    .confidence(0.95)
                    .reasoning(userInput)
                    .build();
        }

        public static IntentResult codeModification(String file, String content) {
            return IntentResult.builder()
                    .type(IntentType.CODE_MODIFICATION)
                    .confidence(0.90)
                    .entity("file", file)
                    .entity("content", content)
                    .build();
        }

        public static IntentResult debugging(String error) {
            return IntentResult.builder()
                    .type(IntentType.DEBUGGING)
                    .confidence(0.85)
                    .entity("error", error)
                    .build();
        }

        public static IntentResult projectAnalysis() {
            return IntentResult.builder()
                    .type(IntentType.PROJECT_ANALYSIS)
                    .confidence(0.80)
                    .build();
        }

        public static IntentResult unknown() {
            return IntentResult.builder()
                    .type(IntentType.UNKNOWN)
                    .confidence(0.0)
                    .build();
        }

        public static IntentResult withConfidence(IntentType type, double confidence) {
            return IntentResult.builder()
                    .type(type)
                    .confidence(confidence)
                    .build();
        }
    }

    public static class Plans {
        public static ExecutionPlan simplePlan(IntentResult intent) {
            ExecutionStep step = ExecutionStep.builder()
                    .id("step-1")
                    .type(StepType.LLM_CALL)
                    .description("处理用户请求")
                    .build();
            
            return ExecutionPlan.builder()
                    .intent(intent)
                    .strategy(ExecutionStrategy.SEQUENTIAL)
                    .step(step)
                    .build();
        }

        public static ExecutionPlan multiStepPlan(IntentResult intent, int stepCount) {
            ExecutionPlan.Builder builder = ExecutionPlan.builder()
                    .intent(intent)
                    .strategy(ExecutionStrategy.SEQUENTIAL);
            
            for (int i = 1; i <= stepCount; i++) {
                builder.step(ExecutionStep.builder()
                        .id("step-" + i)
                        .type(i % 2 == 0 ? StepType.TOOL_CALL : StepType.LLM_CALL)
                        .description("步骤 " + i)
                        .build());
            }
            
            return builder.build();
        }

        public static PlanningContext planningContext(String userInput) {
            return PlanningContext.builder()
                    .userInput(userInput)
                    .currentRound(1)
                    .build();
        }

        public static ExecutionContext executionContext() {
            return ExecutionContext.builder()
                    .build();
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
