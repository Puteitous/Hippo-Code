package com.example.agent.memory.embedding;

import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 向量化调度器
 * 
 * 负责在后台批量向量化未向量化的记忆
 * 触发时机：
 * 1. 系统启动时
 * 2. autoDream 完成整理后
 */
public class VectorizationScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorizationScheduler.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_QUEUE_SIZE = 1000;
    
    private final MemoryStore memoryStore;
    private final EmbeddingService embeddingService;
    private final ExecutorService executor;
    
    private volatile boolean running = false;
    
    public VectorizationScheduler(MemoryStore memoryStore, EmbeddingService embeddingService) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vectorization-scheduler");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动时检查并批量向量化
     */
    public void scheduleOnStartup() {
        if (!embeddingService.isAvailable()) {
            logger.warn("向量化服务不可用，跳过启动时向量化");
            return;
        }
        
        executor.submit(this::vectorizePendingMemories);
    }
    
    /**
     * autoDream 完成后触发向量化
     */
    public void scheduleAfterConsolidation() {
        if (!embeddingService.isAvailable()) {
            logger.debug("向量化服务不可用，跳过整理后向量化");
            return;
        }
        
        executor.submit(this::vectorizePendingMemories);
    }
    
    /**
     * 批量向量化未向量化的记忆
     */
    private void vectorizePendingMemories() {
        if (running) {
            logger.debug("向量化任务正在运行，跳过");
            return;
        }
        
        running = true;
        long startTime = System.currentTimeMillis();
        
        try {
            List<MemoryEntry> pendingEntries = findPendingEntries();
            
            if (pendingEntries.isEmpty()) {
                logger.debug("没有待向量化的记忆");
                return;
            }
            
            logger.info("开始批量向量化，共 {} 条记忆", pendingEntries.size());
            
            int totalProcessed = 0;
            int totalFailed = 0;
            
            // 分批处理
            for (int i = 0; i < pendingEntries.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, pendingEntries.size());
                List<MemoryEntry> batch = pendingEntries.subList(i, end);
                
                try {
                    processBatch(batch);
                    totalProcessed += batch.size();
                } catch (Exception e) {
                    logger.error("批量向量化失败，批次 [{}, {})", i, end, e);
                    totalFailed += batch.size();
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ 批量向量化完成，成功：{}，失败：{}，耗时：{} ms", 
                totalProcessed, totalFailed, duration);
            
        } catch (Exception e) {
            logger.error("向量化调度异常", e);
        } finally {
            running = false;
        }
    }
    
    /**
     * 查找所有未向量化的记忆
     */
    private List<MemoryEntry> findPendingEntries() {
        List<MemoryEntry> pending = new ArrayList<>();
        
        for (var meta : memoryStore.getAllMetas()) {
            if (pending.size() >= MAX_QUEUE_SIZE) {
                break;
            }
            
            MemoryEntry entry = memoryStore.findById(meta.id);
            if (entry != null && !entry.hasEmbedding()) {
                pending.add(entry);
            }
        }
        
        return pending;
    }
    
    /**
     * 处理一批记忆
     */
    private void processBatch(List<MemoryEntry> batch) {
        String[] texts = batch.stream()
            .map(MemoryEntry::getContent)
            .toArray(String[]::new);
        
        final float[][] embeddings = embeddingService.embedBatch(texts);
        
        for (int i = 0; i < batch.size(); i++) {
            final int index = i;
            MemoryEntry entry = batch.get(i);
            entry.setEmbedding(embeddings[index]);
            
            // 更新记忆文件
            memoryStore.update(entry.getId(), e -> {
                e.setEmbedding(embeddings[index]);
            });
        }
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
