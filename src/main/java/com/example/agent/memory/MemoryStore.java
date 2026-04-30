package com.example.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 多文件记忆存储
 * 
 * 核心特性：
 * - 一条记忆一个文件（UUID.md）
 * - 内存索引加速检索
 * - 原子写入（临时文件 → fsync → 重命名）
 * - 文件锁保护并发写入
 * - 沙箱权限检查
 */
public class MemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(MemoryStore.class);

    private static final String MEMORY_DIR = ".hippo/memory";
    private static final String INDEX_FILE = "MEMORY.md";
    private static final String TEMP_SUFFIX = ".tmp";

    // 内存索引（只存元数据，不存内容）
    private final ConcurrentHashMap<String, MemoryEntryMeta> index = new ConcurrentHashMap<>();
    
    // 文件级锁（用于 JVM 内并发控制）
    private final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();
    
    // 沙箱
    private final MemoryToolSandbox sandbox;
    
    // 存储目录
    private final Path memoryDir;
    
    // 索引文件路径
    private final Path indexPath;
    
    // 后台刷新线程（低负载时全量重建索引）
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    /**
     * 记忆条目元数据（轻量级，用于索引）
     */
    public static class MemoryEntryMeta {
        public final String id;
        public String title;
        public MemoryEntry.MemoryType type;
        public double importance;
        public double confidence;
        public Set<String> tags;
        public Instant lastUpdated;
        public Instant lastAccessed;

        public MemoryEntryMeta(MemoryEntry entry) {
            this.id = entry.getId();
            this.title = extractTitle(entry.getContent());
            this.type = entry.getType();
            this.importance = entry.getImportance();
            this.confidence = entry.getConfidence();
            this.tags = new HashSet<>(entry.getTags());
            this.lastUpdated = entry.getLastUpdated();
            this.lastAccessed = entry.getLastAccessed();
        }

        public MemoryEntryMeta(String id, String title, MemoryEntry.MemoryType type, 
                               double importance, double confidence) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.importance = importance;
            this.confidence = confidence;
            this.tags = new HashSet<>();
            this.lastUpdated = Instant.now();
            this.lastAccessed = Instant.now();
        }

        private String extractTitle(String content) {
            if (content == null || content.isEmpty()) {
                return "Untitled";
            }
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("# ")) {
                    return line.substring(2).trim();
                }
                if (!line.isEmpty()) {
                    return line.length() > 50 ? line.substring(0, 50) + "..." : line;
                }
            }
            return "Untitled";
        }
    }

    public MemoryStore(MemoryToolSandbox sandbox) {
        this.sandbox = sandbox;
        this.memoryDir = sandbox.getMemoryRoot();
        this.indexPath = memoryDir.resolve(INDEX_FILE);
        ensureDirectory();
        loadIndex();
    }

    /**
     * 确保存储目录存在
     */
    private void ensureDirectory() {
        try {
            if (!Files.exists(memoryDir)) {
                Files.createDirectories(memoryDir);
            }
        } catch (IOException e) {
            throw new MemoryAccessException("创建记忆目录失败", e);
        }
    }

    /**
     * 加载索引（从 MEMORY.md 或扫描目录）
     */
    private void loadIndex() {
        // 优先从索引文件加载
        if (Files.exists(indexPath)) {
            try {
                loadIndexFromFile();
                
                // 一致性校验：索引数 vs 文件数
                int indexCount = index.size();
                int fileCount = countMemoryFiles();
                
                if (indexCount != fileCount) {
                    logger.warn(
                        "索引与文件数不一致（索引：{}，文件：{}），全量重建索引",
                        indexCount, fileCount
                    );
                    index.clear();
                    scanDirectory();
                }
                
                return;
            } catch (IOException e) {
                logger.warn("从索引文件加载失败，尝试扫描目录", e);
            }
        }
        
        // 回退：扫描目录
        scanDirectory();
    }

    /**
     * 快速统计记忆文件数量（不解析内容）
     */
    private int countMemoryFiles() {
        if (!Files.exists(memoryDir)) {
            return 0;
        }
        
        try (var stream = Files.list(memoryDir)) {
            return (int) stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .filter(p -> !p.getFileName().toString().equals(INDEX_FILE))
                .filter(p -> !p.getFileName().toString().endsWith(TEMP_SUFFIX))
                .count();
        } catch (IOException e) {
            logger.warn("统计文件数失败", e);
            return 0;
        }
    }

    /**
     * 从索引文件加载（快速）
     */
    private void loadIndexFromFile() throws IOException {
        String content = Files.readString(indexPath);
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // 跳过表头分隔符行（如 |----|-------|...）
            if (line.matches("\\|[-\\s|]+\\|")) {
                continue;
            }
            
            // 解析索引行：| UUID | Title | Type | Importance | Confidence | Tags | LastUpdated |
            if (line.startsWith("|") && line.endsWith("|")) {
                MemoryEntryMeta meta = parseIndexLine(line);
                if (meta != null) {
                    index.put(meta.id, meta);
                }
            }
        }
        
        logger.info("从索引文件加载了 {} 条记忆", index.size());
    }

    /**
     * 扫描目录重建索引（较慢，作为备用）
     */
    private void scanDirectory() {
        if (!Files.exists(memoryDir)) {
            logger.debug("记忆目录不存在：{}", memoryDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir, "*.md")) {
            int fileCount = 0;
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (fileName.equals(INDEX_FILE) || fileName.endsWith(TEMP_SUFFIX)) {
                    continue;
                }
                fileCount++;

                try {
                    // 只解析 frontmatter，不解析 body
                    Map<String, Object> frontmatter = FrontmatterParser.parse(file);
                    if (frontmatter.containsKey("id")) {
                        String id = (String) frontmatter.get("id");
                        logger.trace("扫描到记忆文件：{}", id);
                        // 读取完整 entry 构建 meta
                        MemoryEntry entry = FrontmatterParser.parseEntry(file);
                        index.put(id, new MemoryEntryMeta(entry));
                    } else {
                        logger.warn("文件 {} 没有 id 字段", fileName);
                    }
                } catch (IOException e) {
                    logger.warn("解析文件失败：{}", fileName, e);
                }
            }
            
            logger.info("扫描目录加载了 {} 条记忆（共 {} 个文件）", index.size(), fileCount);
        } catch (IOException e) {
            logger.error("扫描记忆目录失败", e);
        }
    }

    /**
     * 解析索引行
     */
    private MemoryEntryMeta parseIndexLine(String line) {
        // 移除首尾的 | 和空格
        line = line.trim();
        if (line.startsWith("|")) line = line.substring(1);
        if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
        
        String[] parts = line.split("\\|");
        if (parts.length < 6) {
            return null;
        }
        
        try {
            String id = parts[0].trim();
            String title = parts[1].trim();
            MemoryEntry.MemoryType type = MemoryEntry.MemoryType.valueOf(parts[2].trim());
            double importance = Double.parseDouble(parts[3].trim());
            double confidence = Double.parseDouble(parts[4].trim());
            
            MemoryEntryMeta meta = new MemoryEntryMeta(id, title, type, importance, confidence);
            
            // 解析 tags
            String[] tagParts = parts[5].trim().split(",");
            for (String tag : tagParts) {
                meta.tags.add(tag.trim());
            }
            
            return meta;
        } catch (Exception e) {
            logger.warn("解析索引行失败：{}", line, e);
            return null;
        }
    }

    // 构造器辅助方法
    private MemoryEntryMeta createMeta(MemoryEntry entry) {
        return new MemoryEntryMeta(entry);
    }

    /**
     * 添加记忆条目
     */
    public void add(MemoryEntry entry) {
        // 1. 沙箱权限检查
        assertCanWrite(entry.getId());
        
        // 2. 写入文件
        writeMemoryFile(entry);
        
        // 3. 更新索引
        index.put(entry.getId(), createMeta(entry));
        
        // 4. 异步更新索引文件
        scheduleIndexUpdate();
        
        logger.debug("添加记忆：{}", entry.getId());
    }

    /**
     * 更新记忆条目
     */
    public void update(String id, java.util.function.Consumer<MemoryEntry> updater) {
        // 1. 沙箱权限检查
        assertCanWrite(id);
        
        // 2. 获取文件锁
        Object lock = getFileLock(id);
        synchronized (lock) {
            try {
                // 3. 读取现有内容
                MemoryEntry entry = findById(id);
                if (entry == null) {
                    throw new MemoryAccessException("记忆不存在：" + id);
                }
                
                // 4. 应用更新
                updater.accept(entry);
                
                // 5. 写回文件
                writeMemoryFile(entry);
                
                // 6. 更新索引
                index.put(id, createMeta(entry));
                
                // 7. 异步更新索引文件
                scheduleIndexUpdate();
                
                logger.debug("更新记忆：{}", id);
            } catch (Exception e) {
                throw new MemoryAccessException("更新记忆失败：" + id, e);
            }
        }
    }

    /**
     * 删除记忆条目
     */
    public void delete(String id) {
        // 1. 沙箱权限检查
        assertCanWrite(id);
        
        // 2. 获取文件锁
        Object lock = getFileLock(id);
        synchronized (lock) {
            try {
                Path file = getMemoryFilePath(id);
                if (Files.exists(file)) {
                    Files.delete(file);
                    index.remove(id);
                    scheduleIndexUpdate();
                    logger.debug("删除记忆：{}", id);
                }
            } catch (IOException e) {
                throw new MemoryAccessException("删除记忆失败：" + id, e);
            }
        }
    }

    /**
     * 根据 ID 查找记忆（按需加载完整内容）
     */
    public MemoryEntry findById(String id) {
        MemoryEntryMeta meta = index.get(id);
        if (meta == null) {
            return null;
        }
        
        // 记录访问
        meta.lastAccessed = Instant.now();
        
        // 从磁盘读取完整内容
        try {
            Path file = getMemoryFilePath(id);
            if (Files.exists(file)) {
                return FrontmatterParser.parseEntry(file);
            }
        } catch (IOException e) {
            logger.warn("读取记忆文件失败：{}", id, e);
        }
        
        return null;
    }

    /**
     * 根据 ID 查找元数据（快速，不读文件）
     */
    public MemoryEntryMeta findMetaById(String id) {
        return index.get(id);
    }

    /**
     * 搜索记忆（基于索引）
     */
    public List<MemoryEntry> search(String query) {
        List<MemoryEntry> results = new ArrayList<>();
        
        for (MemoryEntryMeta meta : index.values()) {
            if (matchesQuery(meta, query)) {
                MemoryEntry entry = findById(meta.id);
                if (entry != null) {
                    results.add(entry);
                }
            }
        }
        
        // 按相关性排序
        results.sort((e1, e2) -> Double.compare(
            RelevanceScorer.calculateRelevance(e2, query),
            RelevanceScorer.calculateRelevance(e1, query)
        ));
        
        return results;
    }

    /**
     * 判断元数据是否匹配查询
     */
    private boolean matchesQuery(MemoryEntryMeta meta, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        
        String queryLower = query.toLowerCase();
        return meta.title.toLowerCase().contains(queryLower) ||
               meta.tags.stream().anyMatch(tag -> tag.toLowerCase().contains(queryLower)) ||
               meta.type.name().toLowerCase().contains(queryLower);
    }

    /**
     * 获取所有记忆的元数据列表
     */
    public Collection<MemoryEntryMeta> getAllMetas() {
        return Collections.unmodifiableCollection(index.values());
    }

    /**
     * 获取索引大小
     */
    public int getIndexSize() {
        return index.size();
    }

    /**
     * 获取实际文件数量
     */
    public int getFileCount() {
        try (Stream<Path> stream = Files.list(memoryDir)) {
            return (int) stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .filter(p -> !p.getFileName().toString().equals(INDEX_FILE))
                .filter(p -> !p.getFileName().toString().endsWith(TEMP_SUFFIX))
                .count();
        } catch (IOException e) {
            logger.warn("统计文件数失败", e);
            return 0;
        }
    }

    /**
     * 写入记忆文件（原子操作 + 文件锁）
     */
    private void writeMemoryFile(MemoryEntry entry) {
        Path file = getMemoryFilePath(entry.getId());
        Path tempFile = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        
        // 获取 JVM 内文件锁
        Object lock = getFileLock(entry.getId());
        synchronized (lock) {
            try {
                // 1. 生成带 frontmatter 的完整内容
                String frontmatter = FrontmatterParser.generate(entry);
                String content = frontmatter + entry.getContent();
                
                // 2. 写入临时文件
                try (FileChannel channel = FileChannel.open(tempFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    
                    channel.write(java.nio.ByteBuffer.wrap(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    channel.force(true); // fsync：确保数据和元数据都刷到磁盘
                }
                
                // 3. 沙箱检查（写操作前最后一次检查）
                assertCanWrite(entry.getId());
                
                // 4. 原子重命名
                Files.move(tempFile, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
                
                logger.trace("原子写入记忆文件：{}", entry.getId());
            } catch (IOException e) {
                // 清理临时文件
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    logger.warn("清理临时文件失败", ex);
                }
                throw new MemoryAccessException("写入记忆文件失败：" + entry.getId(), e);
            }
        }
    }

    /**
     * 获取文件路径
     */
    private Path getMemoryFilePath(String id) {
        return memoryDir.resolve(id + ".md");
    }

    /**
     * 获取文件锁（JVM 内）
     */
    private Object getFileLock(String id) {
        return fileLocks.computeIfAbsent(id, k -> new Object());
    }

    /**
     * 沙箱权限检查
     */
    private void assertCanWrite(String id) {
        if (sandbox == null) {
            return; // 测试模式可能没有沙箱
        }
        
        Path targetPath = getMemoryFilePath(id).toAbsolutePath();
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", targetPath.toString());
        
        MemoryPermissionResult result = sandbox.check("write_file", input);
        if (!result.isAllowed()) {
            throw new MemoryAccessException("写入权限被拒绝：" + result.getMessage());
        }
    }

    /**
     * 异步更新索引文件
     */
    private void scheduleIndexUpdate() {
        backgroundExecutor.submit(() -> {
            try {
                // 延迟一点时间，合并多次更新
                TimeUnit.MILLISECONDS.sleep(100);
                updateIndexFile();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                logger.warn("更新索引文件失败", e);
            }
        });
    }

    /**
     * 更新索引文件（增量或全量）
     */
    private void updateIndexFile() throws IOException {
        // 简单实现：全量重写索引文件
        // 优化：可以只更新变化的行
        
        StringBuilder sb = new StringBuilder();
        sb.append("# MEMORY Index\n\n");
        sb.append("| ID | Title | Type | Importance | Confidence | Tags | Last Updated |\n");
        sb.append("|----|-------|------|------------|------------|------|--------------|\n");
        
        for (MemoryEntryMeta meta : index.values()) {
            sb.append(String.format("| %s | %s | %s | %.1f | %.1f | %s | %s |\n",
                meta.id,
                meta.title,
                meta.type.name(),
                meta.importance,
                meta.confidence,
                String.join(", ", meta.tags),
                meta.lastUpdated != null ? meta.lastUpdated.toString().substring(0, 10) : "N/A"
            ));
        }
        
        // 原子写入索引文件
        Path tempIndex = indexPath.resolveSibling(indexPath.getFileName() + TEMP_SUFFIX);
        Files.writeString(tempIndex, sb.toString());
        Files.move(tempIndex, indexPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        
        logger.trace("更新索引文件，共 {} 条", index.size());
    }

    /**
     * 关闭存储（清理资源）
     */
    public void close() {
        try {
            // 等待后台任务完成
            backgroundExecutor.shutdown();
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
            
            // 确保索引文件已更新
            updateIndexFile();
        } catch (Exception e) {
            logger.error("关闭 MemoryStore 失败", e);
        }
    }

    // ========== Phase 2 占位方法（保持向后兼容） ==========
    
    /**
     * 获取相关记忆作为提示（Phase 2 实现）
     * 当前返回空字符串
     */
    public String getRelevantMemoriesAsPrompt(String context) {
        // TODO: Phase 2 实现记忆检索和提示生成
        return "";
    }

    /**
     * 触发自动梦境（Phase 2 实现）
     * 当前为空操作
     */
    public void triggerAutoDream() {
        // TODO: Phase 2 实现后台记忆巩固
    }

    /**
     * 添加待处理记忆（Phase 2 实现）
     * 当前为空操作
     */
    public void addPendingMemory(String candidate) {
        // TODO: Phase 2 实现待处理记忆队列
        logger.debug("待处理记忆：{}", candidate);
    }
}
