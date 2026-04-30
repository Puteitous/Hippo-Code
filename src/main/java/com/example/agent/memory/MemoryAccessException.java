package com.example.agent.memory;

/**
 * 记忆访问异常
 * 
 * 当记忆存储操作失败时抛出（包括权限拒绝、IO 错误等）
 */
public class MemoryAccessException extends RuntimeException {
    
    public MemoryAccessException(String message) {
        super(message);
    }
    
    public MemoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
