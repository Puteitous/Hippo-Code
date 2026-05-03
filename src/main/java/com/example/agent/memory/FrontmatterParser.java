package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Frontmatter 解析器
 * 
 * 用于解析和生成 Markdown 文件的 frontmatter 元数据
 * 
 * 格式示例：
 * ```markdown
 * ---
 * id: 550e8400-e29b-41d4-a716-446655440000
 * type: USER_PREFERENCE
 * scope: project
 * tags: java, yaml, configuration
 * created_at: 2024-01-15T10:30:00Z
 * last_accessed: 2024-01-15T10:30:00Z
 * access_count: 5
 * ---
 * 
 * # Memory Title
 * 
 * Content here...
 * ```
 */
public class FrontmatterParser {

    private static final String FRONTMATTER_SEPARATOR = "---";

    /**
     * 解析文件的 frontmatter
     * 
     * @param file 要解析的文件路径
     * @return frontmatter 键值对
     */
    public static Map<String, Object> parse(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("文件不存在：" + file);
        }

        String content = Files.readString(file);
        return parseContent(content);
    }

    /**
     * 从内容字符串中解析 frontmatter
     */
    public static Map<String, Object> parseContent(String content) {
        Map<String, Object> frontmatter = new HashMap<>();

        if (content == null || content.trim().isEmpty()) {
            return frontmatter;
        }

        String[] lines = content.split("\n");
        if (lines.length < 2 || !lines[0].trim().equals(FRONTMATTER_SEPARATOR)) {
            // 没有 frontmatter，返回空
            return frontmatter;
        }

        // 查找结束分隔符
        int endIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals(FRONTMATTER_SEPARATOR)) {
                endIndex = i;
                break;
            }
        }

        if (endIndex == -1) {
            // 没有完整的 frontmatter
            return frontmatter;
        }

        // 解析 frontmatter 行
        for (int i = 1; i < endIndex; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                frontmatter.put(key, parseValue(key, value));
            }
        }

        return frontmatter;
    }

    /**
     * 根据键解析值
     */
    private static Object parseValue(String key, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // 移除引号
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        // 特殊类型解析
        return switch (key) {
            case "confidence", "importance" -> parseDouble(value);
            case "access_count" -> parseInt(value);
            case "tags" -> parseTags(value);
            case "created_at", "last_accessed", "last_updated" -> parseInstant(value);
            default -> value;
        };
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Set<String> parseTags(String value) {
        Set<String> tags = new HashSet<>();
        String[] parts = value.split("[,\\s]+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                tags.add(part.trim());
            }
        }
        return tags;
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    /**
     * 生成 frontmatter 字符串
     */
    public static String generate(MemoryEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(FRONTMATTER_SEPARATOR).append("\n");
        sb.append("id: ").append(entry.getId()).append("\n");
        sb.append("type: ").append(entry.getType().name()).append("\n");
        sb.append("scope: ").append(entry.getScope()).append("\n");
        
        if (entry.getTags() != null && !entry.getTags().isEmpty()) {
            sb.append("tags: ").append(String.join(", ", entry.getTags())).append("\n");
        }
        
        sb.append("created_at: ").append(entry.getCreatedAt()).append("\n");
        sb.append("last_accessed: ").append(entry.getLastAccessed()).append("\n");
        sb.append("access_count: ").append(entry.getAccessCount()).append("\n");
        sb.append(FRONTMATTER_SEPARATOR).append("\n\n");
        
        return sb.toString();
    }

    /**
     * 生成索引行（用于 MEMORY.md）
     */
    public static String generateIndexLine(MemoryEntry entry) {
        String title = extractTitle(entry.getContent());
        String hook = generateHook(entry.getContent());
        
        return String.format("- [%s](%s.md) — %s", 
            title, entry.getId(), hook);
    }

    /**
     * 从内容中提取标题
     */
    public static String extractTitle(String content) {
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
                // 返回第一行非空内容（最多 50 字符）
                return line.length() > 50 ? line.substring(0, 50) + "..." : line;
            }
        }
        return "Untitled";
    }

    /**
     * 提取内容中除标题外的部分
     */
    private static String extractContentWithoutTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean foundTitle = false;

        for (String line : lines) {
            if (!foundTitle && line.trim().startsWith("# ")) {
                foundTitle = true;
                continue; // 跳过标题行
            }
            if (!foundTitle && !line.trim().isEmpty()) {
                // 没有标题标记，返回第一行非空内容之后的内容
                foundTitle = true;
                continue;
            }
            sb.append(line).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * 生成一行钩子描述（最多 150 字符）
     */
    public static String generateHook(String content) {
        if (content == null || content.isEmpty()) {
            return "No content";
        }

        // 移除 markdown 标记
        String clean = content.replaceAll("#+", "")
                              .replaceAll("\\*+", "")
                              .replaceAll("`+", "")
                              .trim();
        
        // 取第一行
        int newlineIndex = clean.indexOf('\n');
        if (newlineIndex > 0) {
            clean = clean.substring(0, newlineIndex);
        }

        // 限制长度
        if (clean.length() > 150) {
            clean = clean.substring(0, 147) + "...";
        }

        return clean.isEmpty() ? "No content" : clean;
    }

    /**
     * 解析完整的 MemoryEntry（包括 frontmatter 和正文）
     */
    public static MemoryEntry parseEntry(Path file) throws IOException {
        String content = Files.readString(file);
        Map<String, Object> frontmatter = parseContent(content);
        
        // 提取正文（frontmatter 之后的内容）
        String body = extractBody(content);
        
        // 如果没有 frontmatter，提取标题并去掉 # 标记
        if (frontmatter.isEmpty() && body != null && !body.isEmpty()) {
            body = extractTitle(body) + "\n" + extractContentWithoutTitle(body);
        }
        
        // 从 frontmatter 构建 MemoryEntry
        String id = (String) frontmatter.getOrDefault("id", java.util.UUID.randomUUID().toString());
        String typeStr = (String) frontmatter.getOrDefault("type", "USER_PREFERENCE");
        
        // 兼容旧类型：映射到新类型
        MemoryEntry.MemoryType type = mapToCurrentMemoryType(typeStr);
        
        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) frontmatter.getOrDefault("tags", new HashSet<>());
        
        MemoryEntry entry = new MemoryEntry(id, body, type, tags);
        
        // 设置其他字段
        if (frontmatter.containsKey("scope")) {
            entry.setScope((String) frontmatter.get("scope"));
        }
        if (frontmatter.containsKey("created_at")) {
            // createdAt 是不可变的，这里不设置
        }
        if (frontmatter.containsKey("last_accessed")) {
            entry.recordAccess(); // 简化处理
        }
        
        return entry;
    }

    /**
     * 将旧类型映射到新类型（兼容性处理）
     */
    private static MemoryEntry.MemoryType mapToCurrentMemoryType(String typeStr) {
        try {
            return MemoryEntry.MemoryType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            // 旧类型映射
            return switch (typeStr) {
                case "TECHNICAL_CONTEXT", "DECISION", "LESSON_LEARNED" -> MemoryEntry.MemoryType.PROJECT_CONTEXT;
                case "FACT" -> MemoryEntry.MemoryType.REFERENCE;
                default -> MemoryEntry.MemoryType.USER_PREFERENCE; // 默认
            };
        }
    }

    /**
     * 提取正文内容（frontmatter 之后的部分）
     */
    public static String extractBody(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        String[] lines = content.split("\n");
        if (lines.length < 2 || !lines[0].trim().equals(FRONTMATTER_SEPARATOR)) {
            return content; // 没有 frontmatter，返回全部内容
        }

        // 查找结束分隔符
        int endIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals(FRONTMATTER_SEPARATOR)) {
                endIndex = i;
                break;
            }
        }

        if (endIndex == -1 || endIndex >= lines.length - 1) {
            return ""; // 没有正文
        }

        // 拼接正文
        StringBuilder body = new StringBuilder();
        for (int i = endIndex + 1; i < lines.length; i++) {
            body.append(lines[i]).append("\n");
        }
        return body.toString().trim();
    }
}
