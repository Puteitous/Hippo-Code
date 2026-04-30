package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class BlockerChain implements Blocker {

    private static final Logger logger = LoggerFactory.getLogger(BlockerChain.class);
    private static final long SLOW_BLOCKER_THRESHOLD_MS = 10;
    private static final long SLOW_CHAIN_THRESHOLD_MS = 50;

    private static final AtomicLong slowBlockerCount = new AtomicLong(0);
    private static final AtomicLong slowChainCount = new AtomicLong(0);
    private static final AtomicLong blockedCount = new AtomicLong(0);

    private final List<Blocker> blockers = new ArrayList<>();

    public BlockerChain add(Blocker blocker) {
        blockers.add(blocker);
        return this;
    }

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        long chainStart = System.nanoTime();
        
        for (Blocker blocker : blockers) {
            long blockerStart = System.nanoTime();
            HookResult result = Objects.requireNonNull(
                blocker.check(toolName, arguments),
                "Blocker " + blocker.getClass().getSimpleName() + " returned null"
            );
            long blockerTimeMs = (System.nanoTime() - blockerStart) / 1_000_000;
            
            if (blockerTimeMs > SLOW_BLOCKER_THRESHOLD_MS) {
                slowBlockerCount.incrementAndGet();
                if (logger.isWarnEnabled()) {
                    logger.warn("Blocker {} 耗时异常：{}ms (阈值：{}ms) [tool={}, 累计慢 {} 次]", 
                        blocker.getClass().getSimpleName(), 
                        blockerTimeMs, 
                        SLOW_BLOCKER_THRESHOLD_MS,
                        toolName,
                        slowBlockerCount.get());
                }
            }
            
            if (!result.isAllowed()) {
                long totalMs = (System.nanoTime() - chainStart) / 1_000_000;
                blockedCount.incrementAndGet();
                String argsSummary = arguments != null ? arguments.toString().substring(0, Math.min(100, arguments.toString().length())) : "null";
                logger.info("BLOCKED by {} for tool={}, args={}, totalChainTime={}ms [累计拦截 {} 次]", 
                    blocker.getClass().getSimpleName(), 
                    toolName,
                    argsSummary,
                    totalMs,
                    blockedCount.get());
                return result;
            }
        }
        
        long totalMs = (System.nanoTime() - chainStart) / 1_000_000;
        if (totalMs > SLOW_CHAIN_THRESHOLD_MS) {
            slowChainCount.incrementAndGet();
            if (logger.isWarnEnabled()) {
                logger.warn("BlockerChain 总耗时 {}ms (阈值：{}ms) [tool={}, 累计慢 {} 次]，考虑优化", 
                    totalMs, SLOW_CHAIN_THRESHOLD_MS, toolName, slowChainCount.get());
            }
        }
        
        return HookResult.allow();
    }

    public void onTurnComplete() {
        for (Blocker blocker : blockers) {
            if (blocker instanceof DuplicateToolCallBlocker) {
                ((DuplicateToolCallBlocker) blocker).onTurnComplete();
            }
        }
    }

    public List<Blocker> getBlockers() {
        return new ArrayList<>(blockers);
    }

    public static long getSlowBlockerCount() {
        return slowBlockerCount.get();
    }

    public static long getSlowChainCount() {
        return slowChainCount.get();
    }

    public static long getBlockedCount() {
        return blockedCount.get();
    }

    public static void resetMetrics() {
        slowBlockerCount.set(0);
        slowChainCount.set(0);
        blockedCount.set(0);
        logger.info("BlockerChain 指标已重置");
    }
}
