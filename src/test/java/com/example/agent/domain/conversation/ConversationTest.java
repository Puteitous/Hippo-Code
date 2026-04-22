package com.example.agent.domain.conversation;

import com.example.agent.llm.model.Message;
import com.example.agent.service.TokenEstimatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Conversation 领域对象核心测试")
class ConversationTest {

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation(8000, TokenEstimatorFactory.getDefault());
    }

    @Test
    @DisplayName("✅ 初始状态正确")
    void initialStateShouldBeCorrect() {
        assertThat(conversation.size()).isEqualTo(0);
        assertThat(conversation.getTokenCount()).isEqualTo(0);
        assertThat(conversation.getUsageRatio()).isEqualTo(0.0);
        assertThat(conversation.getSessionId()).isNotNull();
    }

    @Test
    @DisplayName("✅ 添加用户消息")
    void addUserMessage() {
        conversation.addMessage(Message.user("Hello"));

        assertThat(conversation.size()).isEqualTo(1);
        assertThat(conversation.getMessages()).hasSize(1);
        assertThat(conversation.getMessages().get(0).isUser()).isTrue();
    }

    @Test
    @DisplayName("✅ 添加助手消息")
    void addAssistantMessage() {
        conversation.addMessage(Message.assistant("Hi there!"));

        assertThat(conversation.size()).isEqualTo(1);
        assertThat(conversation.getMessages().get(0).isAssistant()).isTrue();
    }

    @Test
    @DisplayName("✅ 添加系统提示词")
    void addSystemMessage() {
        conversation.addMessage(Message.system("You are a helpful assistant"));

        assertThat(conversation.size()).isEqualTo(1);
        assertThat(conversation.getMessages().get(0).isSystem()).isTrue();
    }

    @Test
    @DisplayName("✅ 批量添加消息")
    void addMultipleMessages() {
        List<Message> messages = Arrays.asList(
            Message.system("System prompt"),
            Message.user("Hello"),
            Message.assistant("Hi")
        );

        conversation.addMessages(messages);

        assertThat(conversation.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("✅ 添加空列表不报错")
    void addEmptyMessages() {
        conversation.addMessages(null);
        conversation.addMessages(Arrays.asList());

        assertThat(conversation.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("✅ clear() 后状态归零")
    void clearResetsState() {
        conversation.addMessage(Message.user("Hello"));
        conversation.addMessage(Message.assistant("Hi"));

        assertThat(conversation.size()).isEqualTo(2);

        conversation.clear();

        assertThat(conversation.size()).isEqualTo(0);
        assertThat(conversation.getTokenCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("✅ replaceMessages 全量替换")
    void replaceMessages() {
        conversation.addMessage(Message.user("Old message"));

        List<Message> newMessages = Arrays.asList(
            Message.user("New message 1"),
            Message.assistant("New message 2")
        );

        conversation.replaceMessages(newMessages);

        assertThat(conversation.size()).isEqualTo(2);
        assertThat(conversation.getMessages().get(0).getContent()).contains("New message");
    }

    @Test
    @DisplayName("✅ Token 使用率计算正确")
    void tokenUsageCalculation() {
        conversation.addMessage(Message.user("Hello"));

        assertThat(conversation.getUsageRatio()).isGreaterThan(0.0);
        assertThat(conversation.getUsageRatio()).isLessThan(1.0);
    }

    @Test
    @DisplayName("✅ getMessageCount 一致")
    void messageCountConsistent() {
        conversation.addMessage(Message.user("1"));
        conversation.addMessage(Message.user("2"));
        conversation.addMessage(Message.user("3"));

        assertThat(conversation.getMessageCount()).isEqualTo(3);
        assertThat(conversation.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("✅ getEffectiveMessages 返回工作窗口消息")
    void effectiveMessages() {
        conversation.addMessage(Message.system("System"));
        conversation.addMessage(Message.user("Hello"));

        List<Message> effective = conversation.getEffectiveMessages();

        assertThat(effective).isNotEmpty();
    }

    @Test
    @DisplayName("✅ 自定义 sessionId")
    void customSessionId() {
        String customId = "test-session-123";
        Conversation conv = new Conversation(8000, TokenEstimatorFactory.getDefault(), customId);

        assertThat(conv.getSessionId()).isEqualTo(customId);
    }

    @Test
    @DisplayName("✅ systemPrompt getter/setter 正确")
    void systemPromptGetterSetter() {
        String prompt = "You are a coding assistant";
        conversation.setSystemPrompt(prompt);

        assertThat(conversation.getSystemPrompt()).isEqualTo(prompt);
    }

    @Test
    @DisplayName("✅ shouldMarkForMemory 规则正确")
    void shouldMarkForMemoryRules() {
        Message shortMessage = Message.user("Hi");
        Message longMessage = Message.user("This is a very long message that exceeds twenty characters");
        Message assistantMsg = Message.assistant("This is assistant response");

        assertThat(conversation.shouldMarkForMemory(shortMessage)).isFalse();
        assertThat(conversation.shouldMarkForMemory(longMessage)).isTrue();
        assertThat(conversation.shouldMarkForMemory(assistantMsg)).isFalse();
    }

    @Test
    @DisplayName("✅ 10 线程并发添加消息不崩溃")
    void concurrentMessageAddition() throws InterruptedException {
        int threadCount = 10;
        int messagesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerThread; i++) {
                        conversation.addMessage(Message.user("Message from thread " + threadId + " - " + i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(conversation.size()).isEqualTo(threadCount * messagesPerThread);
    }

    @Test
    @DisplayName("✅ 并发读写没有异常")
    void concurrentReadWrite() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(4);

        executor.submit(() -> {
            for (int i = 0; i < 100; i++) {
                conversation.addMessage(Message.user("Write " + i));
            }
            latch.countDown();
        });

        for (int r = 0; r < 3; r++) {
            executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    conversation.getMessages();
                    conversation.getTokenCount();
                    conversation.size();
                }
                latch.countDown();
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
    }
}
