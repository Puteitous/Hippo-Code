package com.example.agent.execute;

import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.model.FunctionCall;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.ToolCall;
import com.example.agent.service.TokenEstimatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepetitionPatternHook 测试")
class RepetitionPatternHookTest {

    private RepetitionPatternHook hook;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        hook = new RepetitionPatternHook();
        conversation = new Conversation(8000, TokenEstimatorFactory.getDefault());
    }

    @Nested
    @DisplayName("重复模式检测")
    class RepetitionDetectionTests {

        @Test
        @DisplayName("连续 3 次相同工具调用触发终止")
        void threeConsecutiveSameCallsTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file"),
                List.of("read_file"),
                List.of("read_file")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertTrue(result.isShouldStop());
            assertTrue(result.isPreventContinuation());
            assertTrue(result.getReason().contains("连续 3 轮"));
            assertTrue(result.getReason().contains("read_file"));
        }

        @Test
        @DisplayName("连续 2 次相同工具调用不触发终止")
        void twoConsecutiveSameCallsDoNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file"),
                List.of("read_file")
            );

            StopHook.StopHookContext context = createContext(messages, 2);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }

        @Test
        @DisplayName("不同工具调用不触发终止")
        void differentCallsDoNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file"),
                List.of("grep"),
                List.of("read_file")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }

        @Test
        @DisplayName("连续 4 次相同工具调用触发终止")
        void fourConsecutiveSameCallsTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file"),
                List.of("read_file"),
                List.of("read_file"),
                List.of("read_file")
            );

            StopHook.StopHookContext context = createContext(messages, 4);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertTrue(result.isShouldStop());
        }
    }

    @Nested
    @DisplayName("参数感重检测")
    class ParameterAwareDetectionTests {

        @Test
        @DisplayName("list_directory 不同路径：不触发终止")
        void listDirectoryDifferentPathsDoNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("list_directory{\"path\":\".\"}"),
                List.of("list_directory{\"path\":\"src/main/java\"}"),
                List.of("list_directory{\"path\":\"src/main/java/agent\"}")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop(), "不同路径的 list_directory 不应该被拦截");
        }

        @Test
        @DisplayName("list_directory 相同路径：触发终止")
        void listDirectorySamePathTriggersStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("list_directory{\"path\":\"src\"}"),
                List.of("list_directory{\"path\":\"src\"}"),
                List.of("list_directory{\"path\":\"src\"}")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertTrue(result.isShouldStop(), "相同路径的 list_directory 应该被拦截");
        }

        @Test
        @DisplayName("glob 不同 pattern：不触发终止")
        void globDifferentPatternsDoNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("glob{\"pattern\":\"**/*.java\"}"),
                List.of("glob{\"pattern\":\"**/Config.java\"}"),
                List.of("glob{\"pattern\":\"src/**/Config.java\"}")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop(), "不同 pattern 的 glob 不应该被拦截");
        }

        @Test
        @DisplayName("glob 相同 pattern：触发终止")
        void globSamePatternTriggersStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("glob{\"pattern\":\"**/*.java\"}"),
                List.of("glob{\"pattern\":\"**/*.java\"}"),
                List.of("glob{\"pattern\":\"**/*.java\"}")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertTrue(result.isShouldStop(), "相同 pattern 的 glob 应该被拦截");
        }

        @Test
        @DisplayName("read_file 不同文件：不触发终止")
        void readFileDifferentFilesDoNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file{\"path\":\"Config.java\"}"),
                List.of("read_file{\"path\":\"Service.java\"}"),
                List.of("read_file{\"path\":\"Controller.java\"}")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop(), "不同文件的 read_file 不应该被拦截");
        }

        @Test
        @DisplayName("混合参数：部分相同不触发终止")
        void mixedParametersDoNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("list_directory{\"path\":\"src\"}"),
                List.of("list_directory{\"path\":\"test\"}"),
                List.of("list_directory{\"path\":\"src\"}")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop(), "参数交替出现不应该被拦截");
        }
    }

    @Nested
    @DisplayName("多工具调用签名")
    class MultiToolCallTests {

        @Test
        @DisplayName("多个工具调用组合相同触发终止")
        void multipleToolCallsWithSameCombinationTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file", "grep"),
                List.of("read_file", "grep"),
                List.of("read_file", "grep")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertTrue(result.isShouldStop());
            assertTrue(result.getReason().contains("read_file,grep"));
        }

        @Test
        @DisplayName("工具调用顺序不同不触发终止")
        void differentOrderDoesNotTriggerStop() {
            List<Message> messages = createMessagesWithToolCalls(
                List.of("read_file", "grep"),
                List.of("grep", "read_file"),
                List.of("read_file", "grep")
            );

            StopHook.StopHookContext context = createContext(messages, 3);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("空消息列表不触发终止")
        void emptyMessagesDoNotTriggerStop() {
            StopHook.StopHookContext context = createContext(List.of(), 0);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }

        @Test
        @DisplayName("无工具调用的消息不触发终止")
        void messagesWithoutToolCallsDoNotTriggerStop() {
            List<Message> messages = List.of(
                Message.user("分析代码"),
                Message.assistant("好的，我来分析"),
                Message.user("继续")
            );

            StopHook.StopHookContext context = createContext(messages, 2);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }
    }

    private List<Message> createMessagesWithToolCalls(List<String>... toolCallSets) {
        List<Message> messages = new ArrayList<>();

        for (List<String> toolCalls : toolCallSets) {
            List<ToolCall> calls = new ArrayList<>();
            for (String toolName : toolCalls) {
                FunctionCall function = new FunctionCall(toolName, "{}");
                calls.add(new ToolCall("id_" + toolName, function));
            }
            Message msg = Message.assistant("执行工具");
            msg.setToolCalls(calls);
            messages.add(msg);
        }

        return messages;
    }

    private StopHook.StopHookContext createContext(List<Message> messages, int turnCount) {
        return new StopHook.StopHookContext(
            conversation, messages, turnCount, AgentTurnResult.DONE
        );
    }
}
