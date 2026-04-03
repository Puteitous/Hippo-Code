package com.example.agent.tools.concurrent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class FileLockManager {

    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
    
    private static final FileLockManager INSTANCE = new FileLockManager();

    private FileLockManager() {
    }

    public static FileLockManager getInstance() {
        return INSTANCE;
    }

    public <T> T withReadLock(String filePath, Supplier<T> action) {
        String normalizedPath = normalizePath(filePath);
        ReentrantLock lock = fileLocks.computeIfAbsent(normalizedPath, k -> new ReentrantLock());
        
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public <T> T withWriteLock(String filePath, Supplier<T> action) {
        String normalizedPath = normalizePath(filePath);
        ReentrantLock lock = fileLocks.computeIfAbsent(normalizedPath, k -> new ReentrantLock());
        
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public boolean tryAcquireLock(String filePath) {
        String normalizedPath = normalizePath(filePath);
        ReentrantLock lock = fileLocks.computeIfAbsent(normalizedPath, k -> new ReentrantLock());
        return lock.tryLock();
    }

    public void releaseLock(String filePath) {
        String normalizedPath = normalizePath(filePath);
        ReentrantLock lock = fileLocks.get(normalizedPath);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public boolean isLocked(String filePath) {
        String normalizedPath = normalizePath(filePath);
        ReentrantLock lock = fileLocks.get(normalizedPath);
        return lock != null && lock.isLocked();
    }

    private String normalizePath(String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            return path.toString();
        } catch (Exception e) {
            return filePath;
        }
    }

    public void clear() {
        fileLocks.clear();
    }

    public int getLockCount() {
        return fileLocks.size();
    }
}
