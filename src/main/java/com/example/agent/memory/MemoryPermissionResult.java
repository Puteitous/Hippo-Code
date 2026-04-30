package com.example.agent.memory;

/**
 * 工具权限检查结果
 */
public class MemoryPermissionResult {

    private final boolean allowed;
    private final String message;

    private MemoryPermissionResult(boolean allowed, String message) {
        this.allowed = allowed;
        this.message = message;
    }

    public static MemoryPermissionResult allow() {
        return new MemoryPermissionResult(true, null);
    }

    public static MemoryPermissionResult deny(String message) {
        return new MemoryPermissionResult(false, message);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return allowed ? "ALLOWED" : "DENIED: " + message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryPermissionResult that = (MemoryPermissionResult) o;
        return allowed == that.allowed && 
               (message == null ? that.message == null : message.equals(that.message));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(allowed, message);
    }
}
