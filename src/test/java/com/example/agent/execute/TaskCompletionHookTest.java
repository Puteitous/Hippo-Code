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

@DisplayName("TaskCompletionHook 测试")
class TaskCompletionHookTest {

    private TaskCompletionHook hook;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        hook = new TaskCompletionHook();
        conversation = new Conversation(200000, TokenEstimatorFactory.getDefault());
    }

    @Nested
    @DisplayName("完成信号检测")
    class CompletionSignalTests {

        @Test
        @DisplayName("包含完成信号时不触发终止")
        void noStopWithCompletionSignal() {
            List<Message> messages = createReadOnlyMessages(6);
            messages.add(Message.assistant("重构已完成，以下是修改后的代码："));

            StopHook.StopHookContext context = createContext(messages, 7);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }

        @Test
        @DisplayName("包含修改完成信号时不触发终止")
        void noStopWithModificationCompleteSignal() {
            List<Message> messages = createReadOnlyMessages(4);
            messages.add(Message.assistant("修改如下：\n```java\npublic class Foo {}\n```"));

            StopHook.StopHookContext context = createContext(messages, 5);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }
    }

    @Nested
    @DisplayName("写入操作检测")
    class WriteOperationTests {

        @Test
        @DisplayName("最近有写入操作时不触发终止")
        void noStopWithRecentWriteOperation() {
            List<Message> messages = new ArrayList<>();
            
            for (int i = 0; i < 3; i++) {
                messages.add(Message.user("继续分析"));
                messages.add(createAssistantMessage(List.of("read_file")));
            }
            
            messages.add(Message.user("开始修改"));
            messages.add(createAssistantMessage(List.of("write_file")));
            messages.add(createAssistantMessage(List.of("read_file")));

            StopHook.StopHookContext context = createContext(messages, 6);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }

        @Test
        @DisplayName("编辑文件操作不触发终止")
        void noStopWithEditFileOperation() {
            List<Message> messages = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                messages.add(Message.user("继续"));
                messages.add(createAssistantMessage(List.of("read_file")));
            }
            
            messages.add(createAssistantMessage(List.of("edit_file")));

            StopHook.StopHookContext context = createContext(messages, 7);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }
    }

    @Nested
    @DisplayName("停滞检测")
    class StagnationDetectionTests {

        @Test
        @DisplayName("连续 15 轮纯读取触发用户警告")
        void warnAfterFifteenConsecutiveReadOnlyTurns() {
            List<Message> messages = createReadOnlyMessages(15);

            StopHook.StopHookContext context = createContext(messages, 15);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
            assertTrue(result.isWarning());
            assertTrue(result.getReason().contains("要继续吗"));
        }

        @Test
        @DisplayName("未达 15 轮不触发警告")
        void noWarnBeforeFifteenTurns() {
            List<Message> messages = createReadOnlyMessages(14);

            StopHook.StopHookContext context = createContext(messages, 14);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
            assertFalse(result.isWarning());
        }

        @Test
        @DisplayName("少于 3 轮不进行检查")
        void noCheckBeforeThreeTurns() {
            List<Message> messages = createReadOnlyMessages(2);

            StopHook.StopHookContext context = createContext(messages, 2);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
            assertFalse(result.isWarning());
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
        @DisplayName("无工具调用不触发终止")
        void noToolCallsDoNotTriggerStop() {
            List<Message> messages = List.of(
                Message.user("分析代码"),
                Message.assistant("好的，我来分析")
            );

            StopHook.StopHookContext context = createContext(messages, 6);
            StopHook.StopHookResult result = hook.evaluate(context);

            assertFalse(result.isShouldStop());
        }
    }

    private List<Message> createReadOnlyMessages(int turnCount) {
        List<Message> messages = new ArrayList<>();

        for (int i = 0; i < turnCount; i++) {
            messages.add(Message.user("继续分析"));
            messages.add(createAssistantMessage(List.of("read_file")));
        }

        return messages;
    }

    private Message createAssistantMessage(List<String> toolNames) {
        List<ToolCall> toolCalls = new ArrayList<>();
        for (String name : toolNames) {
            FunctionCall function = new FunctionCall(name, "{}");
            toolCalls.add(new ToolCall("id_" + name, function));
        }
        return Message.assistantWithToolCalls(toolCalls);
    }

    private StopHook.StopHookContext createContext(List<Message> messages, int turnCount) {
        return new StopHook.StopHookContext(
            conversation, messages, turnCount, AgentTurnResult.DONE
        );
    }
}
