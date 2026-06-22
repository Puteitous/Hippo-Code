package com.example.agent.tools;

import com.example.agent.web.util.DiffComputer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回滚系统集成测试。
 * <p>
 * 覆盖完整链路：
 * 新建文件 → 编辑文件 → 删除文件 → 回滚预览 → 回滚执行
 * <p>
 * 验证：
 * - 回滚后文件恢复到变更前的正确状态
 * - changes.jsonl 中的变更记录被正确截断
 * - conversation.jsonl 中的对话记录被正确截断
 * - 预览接口返回正确的文件列表
 */
@DisplayName("回滚系统集成测试")
class RollbackSystemIntegrationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DiffComputer diffComputer = DiffComputer.DEFAULT;
    private static final String SESSION_ID = "test-rollback-session";
    private static final String[] FILE_TOOLS = {"edit_file", "write_file", "delete_file"};

    @TempDir
    Path tempDir;

    private Path sessionDir;
    private Path changesFile;

    @BeforeEach
    void setUp() throws Exception {
        // 初始化 FileChangeTracker（使用测试存储目录）
        FileChangeTracker.resetForTest();
        FileChangeTracker.setStorageDirForTest(tempDir);
        // 存储目录格式：{tempDir}/{sessionId}/changes.jsonl
        sessionDir = tempDir.resolve(SESSION_ID);
        Files.createDirectories(sessionDir);
        changesFile = sessionDir.resolve("changes.jsonl");
    }

    @AfterEach
    void tearDown() {
        FileChangeTracker.clearCurrentSessionId();
        FileChangeTracker.clearCurrentToolCallId();
        FileChangeTracker.resetForTest();
    }

    /**
     * 辅助方法：在 FileChangeTracker 中设置测试用 session 上下文。
     */
    private void withSessionContext(String sessionId, String toolCallId, Runnable action) {
        FileChangeTracker.setCurrentSessionId(sessionId);
        FileChangeTracker.setCurrentToolCallId(toolCallId);
        try {
            action.run();
        } finally {
            FileChangeTracker.clearCurrentToolCallId();
            FileChangeTracker.clearCurrentSessionId();
        }
    }

    // =========================================================
    // 核心场景：新建 + 编辑 + 删除 → 回滚验证
    // =========================================================

    @Nested
    @DisplayName("多操作混合场景")
    class MultiOperationScenario {

        @Test
        @DisplayName("新建+编辑+删除后回滚，文件恢复且数据截断正确")
        void fullRollbackFlow() throws Exception {
            // ──── 准备测试文件 ────
            Path dirA = tempDir.resolve("sub");
            Files.createDirectories(dirA);
            Path fileA = dirA.resolve("hello.txt");       // 将被编辑
            Path fileB = dirA.resolve("delete_me.txt");    // 将被删除
            Path fileC = dirA.resolve("new_file.txt");     // 将被新建

            Files.writeString(fileA, "Hello World v1", StandardCharsets.UTF_8);
            Files.writeString(fileB, "I will be deleted", StandardCharsets.UTF_8);
            assertFalse(Files.exists(fileC), "fileC 初始不存在");

            String pathA = fileA.toAbsolutePath().toString();
            String pathB = fileB.toAbsolutePath().toString();
            String pathC = fileC.toAbsolutePath().toString();

            // ──── 模拟工具执行顺序 ────
            // call_001: write_file 新建 fileC
            withSessionContext(SESSION_ID, "call_001", () -> {
                FileChangeTracker.recordChange(pathC, "", "New file content\nLine 2", "write_file", true);
            });
            // 模拟工具实际创建文件
            Files.writeString(fileC, "New file content\nLine 2", StandardCharsets.UTF_8);
            assertTrue(Files.exists(fileC), "fileC 应已被创建");

            // call_002: edit_file 编辑 fileA
            withSessionContext(SESSION_ID, "call_002", () -> {
                FileChangeTracker.recordChange(pathA, "Hello World v1", "Hello World v2", "edit_file", false);
            });
            Files.writeString(fileA, "Hello World v2", StandardCharsets.UTF_8);
            assertEquals("Hello World v2", Files.readString(fileA));

            // call_003: delete_file 删除 fileB
            withSessionContext(SESSION_ID, "call_003", () -> {
                FileChangeTracker.recordChange(pathB, "I will be deleted", "", "delete_file", false);
            });
            Files.deleteIfExists(fileB);
            assertFalse(Files.exists(fileB), "fileB 应已被删除");

            // ──── 验证 changes.jsonl 有 3 条记录 ────
            List<String> changeLines = Files.readAllLines(changesFile, StandardCharsets.UTF_8);
            assertEquals(3, changeLines.size(), "changes.jsonl 应有 3 条记录");

            // ──── 验证 getChangeByToolCallId 可查到所有三条 ────
            assertNotNull(FileChangeTracker.getChangeByToolCallId("call_001"), "call_001 可查到");
            assertNotNull(FileChangeTracker.getChangeByToolCallId("call_002"), "call_002 可查到");
            assertNotNull(FileChangeTracker.getChangeByToolCallId("call_003"), "call_003 可查到");

            // ──── 模拟 collectFileChangesAfterMessage（预览） ────
            List<Map<String, Object>> previewFiles = simulatePreview(callIdsAndPaths());
            assertEquals(3, previewFiles.size(), "预览应列出 3 个文件");

            // 验证预览结果：新建文件 action=delete, 编辑/删除文件 action=restore
            for (Map<String, Object> f : previewFiles) {
                String fp = (String) f.get("filePath");
                String action = (String) f.get("action");
                if (fp.equals(pathC)) {
                    assertEquals("delete", action, "新建文件的预览 action 应为 delete");
                } else {
                    assertEquals("restore", action, "编辑/删除文件的预览 action 应为 restore");
                }
            }

            // ──── 模拟 executeRewindRollback（逆序回滚） ────
            int count = simulateRollback(callIdsAndPaths());
            assertEquals(3, count, "应成功回滚 3 个文件操作");

            // ──── 验证文件状态 ────
            // fileA: 恢复到 v1
            assertTrue(Files.exists(fileA), "fileA 应存在");
            assertEquals("Hello World v1", Files.readString(fileA), "fileA 应恢复到原始内容 v1");

            // fileB: 被删除的文件应恢复
            assertTrue(Files.exists(fileB), "fileB 应被恢复");
            assertEquals("I will be deleted", Files.readString(fileB), "fileB 应恢复到原始内容");

            // fileC: 新建的文件应被删除
            assertFalse(Files.exists(fileC), "fileC（新建文件）应在回滚后被删除");

            // ──── 验证 changes.jsonl 被截断 ────
            List<String> remainingChanges = Files.readAllLines(changesFile, StandardCharsets.UTF_8);
            assertEquals(0, remainingChanges.size(), "changes.jsonl 应已被清空（所有变更已回滚）");

            // ──── 验证 getChangeByToolCallId 已查不到 ────
            assertNull(FileChangeTracker.getChangeByToolCallId("call_001"), "call_001 应已不可查");
            assertNull(FileChangeTracker.getChangeByToolCallId("call_002"), "call_002 应已不可查");
            assertNull(FileChangeTracker.getChangeByToolCallId("call_003"), "call_003 应已不可查");
        }
    }

    // =========================================================
    // 对话专用场景：纯对话无文件操作
    // =========================================================

    @Nested
    @DisplayName("纯对话场景（无文件操作）")
    class NoFileOperationsScenario {

        @Test
        @DisplayName("纯对话回滚：无文件变更应返回空列表")
        void noFileOperations() throws Exception {
            List<Map.Entry<String, String>> emptyEntries = List.of();
            List<Map<String, Object>> preview = simulatePreview(emptyEntries);
            assertEquals(0, preview.size(), "无文件操作时预览应返回空列表");

            int count = simulateRollback(emptyEntries);
            assertEquals(0, count, "无文件操作时回滚计数应为 0");
        }
    }

    // =========================================================
    // 同一文件多次编辑场景
    // =========================================================

    @Nested
    @DisplayName("同一文件多次编辑场景")
    class SameFileMultipleEditsScenario {

        @Test
        @DisplayName("同一文件编辑两次后回滚到最初状态")
        void multipleEditsSameFile() throws Exception {
            Path testFile = tempDir.resolve("multi_edit.txt");
            Files.writeString(testFile, "v0", StandardCharsets.UTF_8);
            String path = testFile.toAbsolutePath().toString();

            // call_001: 第一次编辑 v0→v1
            withSessionContext(SESSION_ID, "call_001", () -> {
                FileChangeTracker.recordChange(path, "v0", "v1", "edit_file", false);
            });
            Files.writeString(testFile, "v1");

            // call_002: 第二次编辑 v1→v2
            withSessionContext(SESSION_ID, "call_002", () -> {
                FileChangeTracker.recordChange(path, "v1", "v2", "edit_file", false);
            });
            Files.writeString(testFile, "v2");

            // 回滚到第一个用户消息之前（逆序：先 v2→v1，再 v1→v0）
            List<Map.Entry<String, String>> entries = new ArrayList<>();
            entries.add(Map.entry(path, "call_001"));
            entries.add(Map.entry(path, "call_002"));

            int count = simulateRollback(entries);
            assertEquals(2, count, "应成功回滚 2 次编辑");

            // 最终文件恢复到 v0
            assertEquals("v0", Files.readString(testFile), "文件应恢复到最初状态 v0");
        }
    }

    // =========================================================
    // 非 UTF-8 文件场景
    // =========================================================

    @Nested
    @DisplayName("非 UTF-8 文件场景")
    class NonUtf8FileScenario {

        @Test
        @DisplayName("非 UTF-8 文件（含中文字符/GBK）删除后回滚可恢复")
        void nonUtf8FileRollback() throws Exception {
            Path testFile = tempDir.resolve("gbk_file.txt");
            // 使用 GBK 编码写入中文（模拟非 UTF-8 文件）
            byte[] gbkBytes = "中文内容 GBK 编码".getBytes("GBK");
            Files.write(testFile, gbkBytes);
            assertTrue(Files.exists(testFile));

            String path = testFile.toAbsolutePath().toString();

            // 模拟 DeleteFileTool 的行为：先读原始字节再记录
            withSessionContext(SESSION_ID, "call_non_utf8", () -> {
                // 使用 originalBytes 参数模拟非 UTF-8 场景
                FileChangeTracker.recordChange(path,
                    new String(gbkBytes, StandardCharsets.ISO_8859_1),
                    gbkBytes,
                    "",
                    "delete_file",
                    false);
            });
            // 删除文件
            Files.deleteIfExists(testFile);
            assertFalse(Files.exists(testFile), "文件应已被删除");

            // 回滚
            List<Map.Entry<String, String>> entries = List.of(Map.entry(path, "call_non_utf8"));
            int count = simulateRollback(entries);
            assertEquals(1, count, "非 UTF-8 文件回滚应成功");

            // 验证文件恢复，内容正确
            assertTrue(Files.exists(testFile), "非 UTF-8 文件应在回滚后恢复");
            byte[] restoredBytes = Files.readAllBytes(testFile);
            assertArrayEquals(gbkBytes, restoredBytes, "回滚后的字节内容应与原始 GBK 一致");
        }
    }

    // =========================================================
    // conversation.jsonl 截断验证
    // =========================================================

    @Nested
    @DisplayName("conversation.jsonl 截断场景")
    class ConversationJsonlTruncationScenario {

        @Test
        @DisplayName("回滚后 conversation.jsonl 被正确截断")
        void jsonlTruncation() throws Exception {
            // 准备一个 conversation.jsonl
            Path jsonlPath = sessionDir.resolve("conversation.jsonl");
            List<String> jsonlLines = new ArrayList<>();
            jsonlLines.add("{\"type\":\"user\",\"uuid\":\"user-msg-1\",\"message\":{\"id\":\"user-msg-1\",\"role\":\"user\",\"content\":\"Do something\"}}");
            jsonlLines.add("{\"type\":\"assistant\",\"uuid\":\"assistant-msg-1\",\"message\":{\"id\":\"assistant-msg-1\",\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_001\",\"function\":{\"name\":\"write_file\",\"arguments\":\"{\\\"path\\\":\\\"" + tempDir.resolve("trunc_test.txt").toString().replace("\\", "\\\\") + "\\\"}\"}}]}}");
            jsonlLines.add("{\"type\":\"user\",\"uuid\":\"user-msg-2\",\"message\":{\"id\":\"user-msg-2\",\"role\":\"user\",\"content\":\"Delete it\"}}");
            jsonlLines.add("{\"type\":\"assistant\",\"uuid\":\"assistant-msg-2\",\"message\":{\"id\":\"assistant-msg-2\",\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call_002\",\"function\":{\"name\":\"delete_file\",\"arguments\":\"{\\\"paths\\\":[\\\"" + tempDir.resolve("trunc_test.txt").toString().replace("\\", "\\\\") + "\\\"]}\"}}]}}");
            jsonlLines.add("{\"type\":\"user\",\"uuid\":\"user-msg-3\",\"message\":{\"id\":\"user-msg-3\",\"role\":\"user\",\"content\":\"This should be truncated\"}}");
            Files.write(jsonlPath, jsonlLines, StandardCharsets.UTF_8);
            assertEquals(5, Files.readAllLines(jsonlPath).size(), "初始应有 5 行");

            // 模拟回滚到 user-msg-1（即截断 user-msg-1 之后的所有行）
            simulateTruncateConversationJsonl(jsonlPath, "user-msg-1");

            List<String> remaining = Files.readAllLines(jsonlPath, StandardCharsets.UTF_8);
            // 截断逻辑：删除从目标 messageId 开始及之后的所有行（包含目标行自身）
            // user-msg-1 是第 1 行，前面无内容，所以保留 0 行
            assertEquals(0, remaining.size(), "回滚后 JSONL 应为空（目标消息是第一行，前面无内容）");
        }

        @Test
        @DisplayName("回滚到中间轮次：截断正确")
        void truncateToMidConversation() throws Exception {
            Path jsonlPath = sessionDir.resolve("conversation.jsonl");
            List<String> jsonlLines = new ArrayList<>();
            jsonlLines.add("{\"type\":\"user\",\"uuid\":\"msg-1\",\"message\":{\"id\":\"msg-1\",\"role\":\"user\",\"content\":\"First\"}}");
            jsonlLines.add("{\"type\":\"assistant\",\"uuid\":\"a-msg-1\",\"message\":{\"id\":\"a-msg-1\",\"role\":\"assistant\",\"content\":\"Response 1\"}}");
            jsonlLines.add("{\"type\":\"user\",\"uuid\":\"msg-2\",\"message\":{\"id\":\"msg-2\",\"role\":\"user\",\"content\":\"Second\"}}");
            jsonlLines.add("{\"type\":\"assistant\",\"uuid\":\"a-msg-2\",\"message\":{\"id\":\"a-msg-2\",\"role\":\"assistant\",\"content\":\"Response 2\"}}");
            jsonlLines.add("{\"type\":\"user\",\"uuid\":\"msg-3\",\"message\":{\"id\":\"msg-3\",\"role\":\"user\",\"content\":\"Third\"}}");
            jsonlLines.add("{\"type\":\"assistant\",\"uuid\":\"a-msg-3\",\"message\":{\"id\":\"a-msg-3\",\"role\":\"assistant\",\"content\":\"Response 3\"}}");
            Files.write(jsonlPath, jsonlLines, StandardCharsets.UTF_8);

            // 回滚到 msg-2：删除 msg-2 及之后的行，保留 msg-1 和 a-msg-1 共 2 行
            simulateTruncateConversationJsonl(jsonlPath, "msg-2");

            List<String> remaining = Files.readAllLines(jsonlPath, StandardCharsets.UTF_8);
            assertEquals(2, remaining.size(), "应保留 msg-1 和 a-msg-1 两行");
        }
    }

    // =========================================================
    // 辅助方法
    // =========================================================

    /**
     * 构造测试数据：(filePath, toolCallId) 对的列表，逆序排列。
     * 用于模拟 executeRewindRollback 的输入。
     */
    private List<Map.Entry<String, String>> callIdsAndPaths() throws Exception {
        Path dirA = tempDir.resolve("sub");
        Path fileA = dirA.resolve("hello.txt");
        Path fileB = dirA.resolve("delete_me.txt");
        Path fileC = dirA.resolve("new_file.txt");

        List<Map.Entry<String, String>> result = new ArrayList<>();
        result.add(Map.entry(fileA.toAbsolutePath().toString(), "call_002"));
        result.add(Map.entry(fileB.toAbsolutePath().toString(), "call_003"));
        result.add(Map.entry(fileC.toAbsolutePath().toString(), "call_001"));
        // 逆序返回（回滚时逆序遍历）
        return result;
    }

    /**
     * 模拟 SessionApiHandler.collectFileChangesAfterMessage 的预览逻辑。
     * 从 toolCallId 列表反查变更记录，生成预览结果。
     */
    private List<Map<String, Object>> simulatePreview(List<Map.Entry<String, String>> entries) {
        Map<String, Map<String, Object>> fileChanges = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : entries) {
            String filePath = entry.getKey();
            String toolCallId = entry.getValue();

            if (fileChanges.containsKey(filePath)) continue;

            FileChangeTracker.FileChange change = FileChangeTracker.getChangeByToolCallId(toolCallId);
            String action;
            int insertions = 0;
            int deletions = 0;

            if (change != null) {
                action = change.newFile ? "delete" : "restore";
                int[] stats = diffComputer.countDiffStats(
                    change.originalContent != null ? change.originalContent : "",
                    change.newContent != null ? change.newContent : "");
                insertions = stats[0];
                deletions = stats[1];
            } else {
                action = "restore";
            }

            Map<String, Object> item = new HashMap<>();
            item.put("filePath", filePath);
            item.put("action", action);
            item.put("insertions", insertions);
            item.put("deletions", deletions);
            fileChanges.put(filePath, item);
        }

        return new ArrayList<>(fileChanges.values());
    }

    /**
     * 模拟 SessionApiHandler.executeRewindRollback 的回滚执行逻辑。
     * 逆序遍历 entries，调用 FileChangeTracker.rollbackByToolCallId。
     * 返回成功回滚的条目数。
     */
    private int simulateRollback(List<Map.Entry<String, String>> entries) {
        int count = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<String, String> entry = entries.get(i);
            boolean ok = FileChangeTracker.rollbackByToolCallId(entry.getKey(), entry.getValue());
            if (ok) count++;
        }
        return count;
    }

    /**
     * 模拟 SessionRewindHandler.truncateConversationJsonl 的截断逻辑。
     * 删除从 messageId 所在行开始及之后的所有行。
     * 使用临时文件 + REPLACE_EXISTING 保证事务性。
     */
    private void simulateTruncateConversationJsonl(Path jsonlPath, String messageId) throws IOException {
        List<String> lines = Files.readAllLines(jsonlPath, StandardCharsets.UTF_8);
        List<String> keptLines = new ArrayList<>();
        boolean passedTarget = false;
        for (String line : lines) {
            if (line.isBlank()) continue;
            try {
                JsonNode lineNode = objectMapper.readTree(line);
                String uuid = lineNode.has("uuid") ? lineNode.get("uuid").asText() : "";
                if (messageId.equals(uuid)) {
                    passedTarget = true;
                    continue;
                }
                if (!passedTarget) {
                    keptLines.add(line);
                }
            } catch (Exception e) {
                if (!passedTarget) {
                    keptLines.add(line);
                }
            }
        }

        Path tempFile = jsonlPath.resolveSibling("conversation.jsonl.tmp");
        Files.write(tempFile, keptLines, StandardCharsets.UTF_8);
        Files.move(tempFile, jsonlPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean isFileTool(String toolName) {
        for (String ft : FILE_TOOLS) {
            if (ft.equals(toolName)) return true;
        }
        return false;
    }

    private static String parseFilePathFromArgs(String argsStr) {
        if (argsStr == null || argsStr.isEmpty()) return null;
        try {
            JsonNode args = objectMapper.readTree(argsStr);
            if (args.has("paths") && args.get("paths").isArray()) {
                return args.get("paths").get(0).asText();
            }
            return args.has("path") ? args.get("path").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
