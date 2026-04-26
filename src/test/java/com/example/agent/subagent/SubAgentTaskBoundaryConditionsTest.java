package com.example.agent.subagent;

import com.example.agent.domain.conversation.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SubAgentTaskBoundaryConditionsTest {

    private Conversation mockConversation;
    private SubAgentTask task;

    @BeforeEach
    void setUp() {
        mockConversation = mock(Conversation.class);
        task = new SubAgentTask("Test Task", mockConversation, 300);
    }

    @Test
    void testMarkCompletedIsIdempotent() {
        task.markCompleted();
        assertTrue(task.isDone());

        assertDoesNotThrow(() -> task.markCompleted(),
            "重复调用 markCompleted() 不应抛出异常");

        assertTrue(task.isDone());
    }

    @Test
    void testMarkFailedIsIdempotent() {
        Exception testException = new RuntimeException("Test Exception");
        task.markFailed(testException);
        assertTrue(task.isDone());

        assertDoesNotThrow(() -> task.markFailed(new RuntimeException("Another Exception")),
            "重复调用 markFailed() 不应抛出异常");

        assertTrue(task.isDone());
    }

    @Test
    void testMarkCompletedAfterMarkFailedDoesNothing() {
        task.markFailed(new RuntimeException("Failed first"));
        assertTrue(task.isDone());

        task.markCompleted();

        assertThrows(ExecutionException.class,
            () -> task.getCompletionFuture().get(1, TimeUnit.SECONDS),
            "状态应保持为 FAILED，不应变为 COMPLETED");
    }

    @Test
    void testMarkFailedAfterMarkCompletedDoesNothing() {
        task.markCompleted();
        assertTrue(task.isDone());

        task.markFailed(new RuntimeException("Should not change"));

        assertDoesNotThrow(() -> task.getCompletionFuture().get(1, TimeUnit.SECONDS),
            "状态应保持为 COMPLETED，不应变为 FAILED");
    }

    @Test
    void testCancelSetsDoneStatus() {
        assertFalse(task.isDone());

        boolean result = task.cancel();
        assertTrue(result);
        assertTrue(task.isDone());
        assertTrue(task.getCompletionFuture().isCancelled());
    }

    @Test
    void testCancelIsIdempotent() {
        task.cancel();
        assertTrue(task.isCancelled());

        boolean secondCancelResult = task.cancel();
        assertFalse(secondCancelResult, "第二次 cancel() 应返回 false");
        assertTrue(task.isCancelled());
    }

    @Test
    void testTimeoutDetection() throws InterruptedException {
        SubAgentTask shortTask = new SubAgentTask("Timeout Test", mockConversation, 1);
        assertFalse(shortTask.isTimeout());

        Thread.sleep(1100);

        assertTrue(shortTask.isTimeout(), "1 秒后任务应检测为超时");
    }

    @Test
    void testShouldStopExecutionForCancelledTask() {
        assertFalse(task.shouldStopExecution());

        task.cancel();

        assertTrue(task.shouldStopExecution(), "已取消任务应停止执行");
    }

    @Test
    void testAwaitCompletionCompletesWhenMarkCompleted() throws Exception {
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.markCompleted();
        }).start();

        SubAgentTask result = task.awaitCompletion(5, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.isDone());
    }

    @Test
    void testAwaitCompletionThrowsWhenMarkFailed() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.markFailed(new RuntimeException("Test Failure"));
        }).start();

        assertThrows(ExecutionException.class,
            () -> task.awaitCompletion(5, TimeUnit.SECONDS),
            "标记失败时 awaitCompletion 应抛出异常");
    }

    @Test
    void testAwaitCompletionThrowsTimeout() {
        assertThrows(TimeoutException.class,
            () -> task.awaitCompletion(100, TimeUnit.MILLISECONDS),
            "超时时应抛出 TimeoutException");
    }

    @Test
    void testAddLogWithNullDoesNotCrash() {
        assertDoesNotThrow(() -> task.addLog(null),
            "addLog(null) 不应抛出 NPE");
    }

    @Test
    void testAddLogWithEmptyDoesNotCrash() {
        assertDoesNotThrow(() -> task.addLog(""),
            "addLog(\"\") 不应抛出异常");
    }

    @Test
    void testConstructorWithNullDependsOnDoesNotCrash() {
        assertDoesNotThrow(() -> new SubAgentTask("Test", mockConversation, 300, null),
            "null dependsOn 不应崩溃");
    }

    @Test
    void testConstructorWithNegativeTimeoutDetectsImmediately() {
        SubAgentTask negativeTask = new SubAgentTask("Test", mockConversation, -100);
        assertNotNull(negativeTask);
        assertTrue(negativeTask.isTimeout(), "负超时应立即检测为超时");
        assertTrue(negativeTask.shouldStopExecution(), "负超时应立即停止执行");
    }

    @Test
    void testIsDoneInitialState() {
        assertFalse(task.isDone(), "新建任务初始状态 should not be done");
    }

    @Test
    void testCompletionFutureCompletesOnlyOnce() throws InterruptedException {
        int[] completionCounter = {0};
        task.getCompletionFuture().whenComplete((result, error) -> {
            completionCounter[0]++;
        });

        task.markCompleted();
        task.markCompleted();
        task.markFailed(new RuntimeException("Test"));

        Thread.sleep(100);

        assertEquals(1, completionCounter[0], "Future 应该只完成一次");
    }
}
