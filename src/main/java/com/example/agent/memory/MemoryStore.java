package com.example.agent.memory;

import com.example.agent.llm.client.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class MemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(MemoryStore.class);

    private final List<MemoryEntry> memories = new CopyOnWriteArrayList<>();
    private final Set<String> pendingMemories = ConcurrentHashMap.newKeySet();
    private final LlmClient llmClient;
    private final Path memoryFilePath;

    public MemoryStore(LlmClient llmClient) {
        this(llmClient, System.getProperty("user.dir"));
    }

    public MemoryStore(LlmClient llmClient, String workspacePath) {
        this.llmClient = llmClient;
        this.memoryFilePath = Paths.get(workspacePath, "MEMORY.md");
        load();
    }

    private void load() {
        if (!Files.exists(memoryFilePath)) {
            return;
        }

        try {
            String content = Files.readString(memoryFilePath);
            parseMemoryFile(content);
        } catch (IOException e) {
            logger.warn("加载记忆文件失败", e);
        }
    }

    private void parseMemoryFile(String content) {
        String[] lines = content.split("\n");
        MemoryEntry.MemoryType currentType = null;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("## ") && !line.startsWith("### ")) {
                String typeName = line.substring(3).trim();
                currentType = parseTypeFromString(typeName);
            } else if (line.startsWith("### ") && currentType != null) {
                String header = line.substring(4).trim();
                header = header.replaceFirst("^#+\\s*", "");

                Set<String> tags = extractTags(header);
                memories.add(new MemoryEntry(
                    UUID.randomUUID().toString(),
                    header,
                    currentType,
                    tags,
                    0.7
                ));
            }
        }
    }

    private MemoryEntry.MemoryType parseTypeFromString(String typeName) {
        if (typeName == null) return MemoryEntry.MemoryType.FACT;
        String normalized = typeName.trim();
        if ("用户偏好".equals(normalized)) return MemoryEntry.MemoryType.USER_PREFERENCE;
        if ("技术上下文".equals(normalized)) return MemoryEntry.MemoryType.TECHNICAL_CONTEXT;
        if ("关键决策".equals(normalized)) return MemoryEntry.MemoryType.DECISION;
        if ("经验教训".equals(normalized)) return MemoryEntry.MemoryType.LESSON_LEARNED;
        if ("项目上下文".equals(normalized)) return MemoryEntry.MemoryType.PROJECT_CONTEXT;
        return MemoryEntry.MemoryType.FACT;
    }

    private Set<String> extractTags(String header) {
        Set<String> tags = new HashSet<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(header);
        while (matcher.find()) {
            String[] tagArray = matcher.group(1).split("[,，]");
            for (String tag : tagArray) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private MemoryEntry.MemoryType extractType(String header) {
        String lower = header.toLowerCase();

        if (lower.contains("偏好") || lower.contains("preference")) return MemoryEntry.MemoryType.USER_PREFERENCE;
        if (lower.contains("决策") || lower.contains("decision")) return MemoryEntry.MemoryType.DECISION;
        if (lower.contains("教训") || lower.contains("lesson") || lower.contains("踩坑") || lower.contains("经验")) return MemoryEntry.MemoryType.LESSON_LEARNED;
        if (lower.contains("技术") || lower.contains("technical") || lower.contains("框架") || lower.contains("库")) return MemoryEntry.MemoryType.TECHNICAL_CONTEXT;
        if (lower.contains("项目") || lower.contains("project") || lower.contains("架构") || lower.contains("模块")) return MemoryEntry.MemoryType.PROJECT_CONTEXT;
        return MemoryEntry.MemoryType.FACT;
    }

    public void addPendingMemory(String memoryCandidate) {
        if (memoryCandidate != null && !memoryCandidate.isBlank()) {
            pendingMemories.add(memoryCandidate);
        }
    }

    public void triggerAutoDream() {
        if (pendingMemories.size() < 3) {
            return;
        }

        List<String> candidates = new ArrayList<>(pendingMemories);
        pendingMemories.clear();

        new Thread(() -> processAutoDream(candidates)).start();
    }

    private void processAutoDream(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        String prompt = String.format(
            "## Auto Dream - 记忆整理任务\n\n" +
            "请从以下候选记忆中筛选出真正值得长期记住的内容。\n" +
            "记住标准：\n" +
            "- 用户明确的偏好设置\n" +
            "- 重要的技术决策和架构选择\n" +
            "- 踩过的坑和经验教训\n" +
            "- 项目关键上下文\n\n" +
            "不要记住：临时调试信息、中间状态、一次性工具输出。\n\n" +
            "候选记忆：\n%s\n\n" +
            "输出格式：每条记忆一行，使用 ### 开头，后跟标签，如 ### 用户偏好 (java, style)",
            String.join("\n\n---\n\n", candidates)
        );

        try {
            String result = llmClient.generateSync(prompt);
            processDreamResult(result);
        } catch (Exception e) {
            logger.warn("生成记忆失败", e);
        }
    }

    private void processDreamResult(String result) {
        if (result == null || result.isBlank()) {
            return;
        }

        Set<String> existingContents = memories.stream()
            .map(MemoryEntry::getContent)
            .collect(Collectors.toSet());

        String[] lines = result.split("\n");
        boolean hasNewMemory = false;

        for (String line : lines) {
            line = line.trim();
            line = line.replaceFirst("^###\\s+#+\\s*", "### ");

            if (line.startsWith("### ")) {
                String[] parts = line.split(" ", 2);
                if (parts.length >= 2) {
                    String header = parts[1];
                    if (!existingContents.contains(header)) {
                        Set<String> tags = extractTags(header);
                        MemoryEntry.MemoryType type = extractType(header);

                        memories.add(new MemoryEntry(
                            UUID.randomUUID().toString(),
                            header,
                            type,
                            tags,
                            0.8
                        ));
                        existingContents.add(header);
                        hasNewMemory = true;
                    }
                }
            }
        }

        if (hasNewMemory) {
            save();
        }
    }

    private synchronized void save() {
        StringBuilder sb = new StringBuilder();
        sb.append("# MEMORY.md - Hippo Agent 长期记忆\n\n");
        sb.append("> 本文件由 Auto Dream 自动整理，也可手动编辑\n\n");
        sb.append("---\n\n");

        Map<MemoryEntry.MemoryType, List<MemoryEntry>> byType = memories.stream()
            .collect(Collectors.groupingBy(MemoryEntry::getType));

        for (Map.Entry<MemoryEntry.MemoryType, List<MemoryEntry>> entry : byType.entrySet()) {
            sb.append("## ").append(getTypeName(entry.getKey())).append("\n\n");
            for (MemoryEntry mem : entry.getValue()) {
                String tags = String.join(", ", mem.getTags());
                sb.append("### ").append(mem.getContent());
                if (!tags.isEmpty()) {
                    sb.append(" (").append(tags).append(")");
                }
                sb.append("\n\n");
            }
        }

        try {
            Files.writeString(memoryFilePath, sb.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            logger.warn("保存记忆文件失败", e);
        }
    }

    private String getTypeName(MemoryEntry.MemoryType type) {
        switch (type) {
            case USER_PREFERENCE: return "用户偏好";
            case TECHNICAL_CONTEXT: return "技术上下文";
            case DECISION: return "关键决策";
            case LESSON_LEARNED: return "经验教训";
            case PROJECT_CONTEXT: return "项目上下文";
            default: return "事实记忆";
        }
    }

    public List<MemoryEntry> searchRelevant(String query, int limit) {
        if (query == null || query.isBlank() || memories.isEmpty()) {
            return new ArrayList<>();
        }

        return memories.stream()
            .sorted((a, b) -> Double.compare(
                b.calculateRelevance(query),
                a.calculateRelevance(query)
            ))
            .filter(m -> m.calculateRelevance(query) > 0.1)
            .limit(Math.max(1, limit))
            .collect(Collectors.toList());
    }

    public String getRelevantMemoriesAsPrompt(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        List<MemoryEntry> relevant = searchRelevant(query, 8);
        if (relevant.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 🧠 相关记忆 (从 MEMORY.md 检索)\n\n");
        for (MemoryEntry mem : relevant) {
            sb.append("- ").append(mem.getContent()).append("\n");
        }
        sb.append("\n---\n\n");
        return sb.toString();
    }

    public List<MemoryEntry> getAllMemories() {
        return new ArrayList<>(memories);
    }

    public int getPendingCount() {
        return pendingMemories.size();
    }

    public int size() {
        return memories.size();
    }
}
