package com.example.agent.memory;

/**
 * @deprecated 已迁移到 {@link com.example.agent.memory.session.SessionMemoryManager}
 * 保留此类仅为向后兼容，新代码应使用 memory.session 包中的版本
 */
@Deprecated
public class SessionMemoryManager extends com.example.agent.memory.session.SessionMemoryManager {
    
    public SessionMemoryManager(String sessionId) {
        super(sessionId);
    }
    
    public SessionMemoryManager(String sessionId, java.nio.file.Path baseDir) {
        super(sessionId, baseDir);
    }
}
