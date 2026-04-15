package com.example.agent.core.concurrency;

import com.example.agent.core.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPools {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPools.class);

    private static final Map<String, ExecutorService> EXECUTORS = new ConcurrentHashMap<>();
    private static volatile boolean shutdownHookRegistered = false;

    private ThreadPools() {}

    public static final class Names {
        public static final String TOOL_EXECUTOR = "tool-executor";
        public static final String MCP_SCHEDULER = "mcp-scheduler";
        public static final String CACHE_MONITOR = "cache-monitor";
        public static final String MCP_IO = "mcp-io";
        public static final String JSONRPC_CLEANUP = "jsonrpc-cleanup";
        public static final String ASYNC_GENERAL = "async-general";
    }

    public static synchronized void initialize() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(ThreadPools::shutdownAll));
            shutdownHookRegistered = true;
        }

        EXECUTORS.put(Names.TOOL_EXECUTOR,
                Executors.newVirtualThreadPerTaskExecutor());

        EXECUTORS.put(Names.ASYNC_GENERAL,
                Executors.newVirtualThreadPerTaskExecutor());

        EXECUTORS.put(Names.MCP_SCHEDULER,
                Executors.newSingleThreadScheduledExecutor(namedThreadFactory("mcp-scheduler", true)));

        EXECUTORS.put(Names.CACHE_MONITOR,
                Executors.newSingleThreadScheduledExecutor(namedThreadFactory("cache-monitor", true)));

        EXECUTORS.put(Names.JSONRPC_CLEANUP,
                Executors.newSingleThreadScheduledExecutor(namedThreadFactory("jsonrpc-cleanup", true)));

        EXECUTORS.put(Names.MCP_IO,
                Executors.newCachedThreadPool(namedThreadFactory("mcp-io-", false)));

        logger.info("线程池管理器初始化完成 ✅ (共 {} 个池)", EXECUTORS.size());
    }

    @SuppressWarnings("unchecked")
    public static <T extends ExecutorService> T get(String name) {
        ExecutorService executor = EXECUTORS.get(name);
        if (executor == null) {
            throw new IllegalArgumentException("Unknown executor pool: " + name);
        }
        return (T) executor;
    }

    public static ExecutorService toolExecutor() {
        return get(Names.TOOL_EXECUTOR);
    }

    public static ScheduledExecutorService mcpScheduler() {
        return get(Names.MCP_SCHEDULER);
    }

    public static ScheduledExecutorService cacheMonitor() {
        return get(Names.CACHE_MONITOR);
    }

    public static ExecutorService mcpIoExecutor() {
        return get(Names.MCP_IO);
    }

    public static ScheduledExecutorService jsonRpcCleanupExecutor() {
        return get(Names.JSONRPC_CLEANUP);
    }

    public static ExecutorService asyncGeneral() {
        return get(Names.ASYNC_GENERAL);
    }

    public static ThreadFactory namedThreadFactory(String prefix, boolean daemon) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
                t.setDaemon(daemon);
                t.setUncaughtExceptionHandler((thread, e) ->
                        logger.error("线程 {} 未捕获异常: {}", thread.getName(), e.getMessage(), e));
                return t;
            }
        };
    }

    public static Runnable wrapWithMdc(Runnable runnable) {
        Map<String, String> snapshot = LoggingContext.snapshot();
        return () -> {
            LoggingContext.restore(snapshot);
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }

    private static void shutdownAll() {
        logger.info("正在关闭所有线程池...");
        EXECUTORS.forEach((name, executor) -> {
            try {
                executor.shutdownNow();
                logger.debug("线程池 {} 已关闭", name);
            } catch (Exception e) {
                logger.warn("关闭线程池 {} 失败: {}", name, e.getMessage());
            }
        });
        logger.info("所有线程池已关闭");
    }
}
