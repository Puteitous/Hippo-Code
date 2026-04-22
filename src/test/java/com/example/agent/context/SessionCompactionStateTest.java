package com.example.agent.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionCompactionStateTest {

    @Test
    void 初始状态_应该允许压缩() {
        SessionCompactionState state = new SessionCompactionState();
        assertTrue(state.shouldTryCompaction(), "初始状态应该允许压缩尝试");
        assertEquals(0, state.getConsecutiveFailures());
        assertEquals(0, state.getCompactionCount());
    }

    @Test
    void 失败1次_仍允许尝试() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        assertTrue(state.shouldTryCompaction());
        assertEquals(1, state.getConsecutiveFailures());
    }

    @Test
    void 失败2次_仍允许尝试() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        assertTrue(state.shouldTryCompaction());
        assertEquals(2, state.getConsecutiveFailures());
    }

    @Test
    void 失败3次_断路器熔断_不再允许尝试() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        state.recordFailure();
        assertFalse(state.shouldTryCompaction(), "连续失败 3 次应该触发断路器");
        assertEquals(3, state.getConsecutiveFailures());
    }

    @Test
    void 失败超过3次_保持熔断状态() {
        SessionCompactionState state = new SessionCompactionState();
        for (int i = 0; i < 10; i++) {
            state.recordFailure();
        }
        assertFalse(state.shouldTryCompaction());
        assertEquals(10, state.getConsecutiveFailures());
    }

    @Test
    void 失败2次后成功_计数重置为0() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        assertEquals(2, state.getConsecutiveFailures());

        state.recordSuccess();
        assertEquals(0, state.getConsecutiveFailures());
        assertTrue(state.shouldTryCompaction());
    }

    @Test
    void 刚好失败3次前成功_断路器不触发() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        assertEquals(2, state.getConsecutiveFailures());
        assertTrue(state.shouldTryCompaction());

        state.recordSuccess();
        assertEquals(0, state.getConsecutiveFailures());
        assertTrue(state.shouldTryCompaction());

        state.recordFailure();
        assertEquals(1, state.getConsecutiveFailures());
        assertTrue(state.shouldTryCompaction());
    }

    @Test
    void 压缩成功后_同Loop不再允许压缩() {
        SessionCompactionState state = new SessionCompactionState();
        assertTrue(state.shouldTryCompaction());

        state.recordSuccess();
        assertFalse(state.shouldTryCompaction(), "同 Loop 压缩成功后应该禁止重入");
        assertEquals(0, state.getConsecutiveFailures());
    }

    @Test
    void 新QueryLoop_重置同Loop标记_但保留失败计数() {
        SessionCompactionState state = new SessionCompactionState();

        state.recordFailure();
        state.recordSuccess();
        assertFalse(state.shouldTryCompaction());

        state.startNewQueryLoop();
        assertTrue(state.shouldTryCompaction(), "新 Loop 应该允许再次压缩");
        assertEquals(0, state.getConsecutiveFailures(), "成功后失败计数保持 0");
    }

    @Test
    void 失败2次后新Loop_仍保留失败计数() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        assertEquals(2, state.getConsecutiveFailures());

        state.startNewQueryLoop();
        assertEquals(2, state.getConsecutiveFailures(), "失败计数是会话级的，跨 Loop 不重置");
        assertTrue(state.shouldTryCompaction());
    }

    @Test
    void 失败3次熔断后_新Loop仍保持熔断() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        state.recordFailure();
        assertFalse(state.shouldTryCompaction());

        state.startNewQueryLoop();
        assertFalse(state.shouldTryCompaction(), "断路器是永久熔断，新 Loop 也不允许");
        assertEquals(3, state.getConsecutiveFailures());
    }

    @Test
    void Reset_重置所有状态() {
        SessionCompactionState state = new SessionCompactionState();
        state.recordFailure();
        state.recordFailure();
        state.recordFailure();
        state.recordCompaction();
        state.recordSuccess();
        state.startNewQueryLoop();

        state.reset();
        assertEquals(0, state.getConsecutiveFailures());
        assertEquals(0, state.getCompactionCount());
        assertTrue(state.shouldTryCompaction());
    }

    @Test
    void 完整状态机流程_验证() {
        SessionCompactionState state = new SessionCompactionState();

        assertTrue(state.shouldTryCompaction());
        state.recordFailure();
        assertTrue(state.shouldTryCompaction());
        state.recordFailure();
        assertTrue(state.shouldTryCompaction());
        state.recordSuccess();
        assertFalse(state.shouldTryCompaction());

        state.startNewQueryLoop();
        assertTrue(state.shouldTryCompaction());
        state.recordFailure();
        assertTrue(state.shouldTryCompaction());
        state.recordFailure();
        assertTrue(state.shouldTryCompaction());
        state.recordFailure();
        assertFalse(state.shouldTryCompaction());

        state.startNewQueryLoop();
        assertFalse(state.shouldTryCompaction());
    }
}
