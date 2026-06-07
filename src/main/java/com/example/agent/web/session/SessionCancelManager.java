package com.example.agent.web.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 会话级取消标志管理器。
 * <p>
 * 解决前端 abortController.abort() 断开 TCP 后，后端因 TCP 缓冲区延迟无法及时感知断开的问题。
 * 提供共享的取消标志，供 Agent 循环和 LLM 流式读取线程检查。
 * </p>
 */
public class SessionCancelManager {

    private static final SessionCancelManager INSTANCE = new SessionCancelManager();

    private final ConcurrentHashMap<String, AtomicBoolean> cancelledSessions = new ConcurrentHashMap<>();

    private SessionCancelManager() {}

    public static SessionCancelManager getInstance() {
        return INSTANCE;
    }

    /**
     * 标记指定会话为已取消。
     * 线程安全，可被 HTTP 线程（ToolAbortHandler）调用。
     */
    public void cancel(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            cancelledSessions.computeIfAbsent(sessionId, k -> new AtomicBoolean(false)).set(true);
        }
    }

    /**
     * 检查指定会话是否已被取消。
     * 供 Agent 循环检查点和 LLM 流式读取线程使用。
     */
    public boolean isCancelled(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        AtomicBoolean flag = cancelledSessions.get(sessionId);
        return flag != null && flag.get();
    }

    /**
     * 清理会话的取消标志。
     * 新一轮请求开始前调用，避免旧标志影响新请求。
     */
    public void reset(String sessionId) {
        if (sessionId != null) {
            cancelledSessions.remove(sessionId);
        }
    }

    /**
     * 清理所有取消标志。
     */
    public void resetAll() {
        cancelledSessions.clear();
    }
}
