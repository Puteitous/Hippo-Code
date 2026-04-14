package com.example.agent.domain.index.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InvertedIndexSearch 鲁棒性测试
 *
 * 测试重点：
 * - 异常场景不崩溃
 * - 单个文件失败不影响整体
 * - 目录深度限制生效
 * - P2 级修复验证
 */
class InvertedIndexSearchRobustnessTest {

    private InvertedIndexSearch searchEngine;

    @BeforeEach
    void setUp() {
        searchEngine = new InvertedIndexSearch();
    }

    @Test
    @DisplayName("边界 - buildIndex 空项目不崩溃")
    void testBuildIndexEmptyProject() {
        assertDoesNotThrow(() -> {
            searchEngine.buildIndex();
        });
    }

    @Test
    @DisplayName("边界 - search null 查询返回空列表")
    void testSearchNullQuery() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search(null, 10);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        });
    }

    @Test
    @DisplayName("边界 - search 空字符串查询")
    void testSearchEmptyQuery() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search("", 10);
            assertNotNull(results);
        });
    }

    @Test
    @DisplayName("边界 - maxResults 负数")
    void testSearchNegativeMaxResults() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search("test", -5);
            assertNotNull(results);
        });
    }

    @Test
    @DisplayName("边界 - maxResults 为零")
    void testSearchZeroMaxResults() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search("test", 0);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        });
    }

    @Test
    @DisplayName("边界 - maxResults 极大值")
    void testSearchVeryLargeMaxResults() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search("test", Integer.MAX_VALUE);
            assertNotNull(results);
        });
    }

    @Test
    @DisplayName("边界 - 空索引 search 不报错")
    void testSearchOnEmptyIndex() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search("test", 10);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        });
    }

    @Test
    @DisplayName("边界 - getIndexSize 空索引返回 0")
    void testGetIndexSizeEmpty() {
        assertEquals(0, searchEngine.getIndexSize());
    }

    @Test
    @DisplayName("鲁棒性 - buildIndex 包含深层嵌套目录")
    void testDeepNestedDirectories(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path deepDir = tempDir;
        for (int i = 0; i < 20; i++) {
            deepDir = deepDir.resolve("level" + i);
            Files.createDirectories(deepDir);
        }

        Path deepFile = deepDir.resolve("DeepClass.java");
        Files.writeString(deepFile, "public class DeepClass {}");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());

            assertDoesNotThrow(() -> {
                searchEngine.buildIndex();
            }, "深层目录不应栈溢出或崩溃");

        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("鲁棒性 - 深度限制应生效（最多15层）")
    void testDirectoryDepthLimit(@TempDir Path tempDir) throws IOException, InterruptedException {
        for (int level = 0; level < 20; level++) {
            Path dir = tempDir;
            for (int i = 0; i < level; i++) {
                dir = dir.resolve("d" + i);
            }
            Files.createDirectories(dir);
            Path javaFile = dir.resolve("ClassAtLevel" + level + ".java");
            Files.writeString(javaFile, "public class ClassAtLevel" + level + " {}");
        }

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());

            searchEngine.buildIndex();

            int indexSize = searchEngine.getIndexSize();
            assertTrue(indexSize >= 15, "至少应能索引到15层深度的文件");
            assertTrue(indexSize <= 16, "超过15层深度的文件不应被索引");

        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("鲁棒性 - 各种扩展名文件处理")
    void testVariousFileExtensions(@TempDir Path tempDir) throws IOException, InterruptedException {
        String[] extensions = {"java", "py", "js", "ts", "md", "xml", "json", "txt", "log", "unknown"};

        for (String ext : extensions) {
            Path file = tempDir.resolve("TestFile." + ext);
            Files.writeString(file, "public class Test {}");
        }

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());

            assertDoesNotThrow(() -> {
                searchEngine.buildIndex();
            }, "各种扩展名文件都应能处理");

        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("鲁棒性 - 忽略目录应被正确过滤")
    void testIgnoredDirectories(@TempDir Path tempDir) throws IOException, InterruptedException {
        String[] ignoredDirs = {"node_modules", "target", "build", ".git", ".idea"};

        for (String dirName : ignoredDirs) {
            Path ignoredDir = tempDir.resolve(dirName);
            Files.createDirectories(ignoredDir);
            Path file = ignoredDir.resolve("IgnoredClass.java");
            Files.writeString(file, "public class IgnoredClass {}");
        }

        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path realFile = srcDir.resolve("RealClass.java");
        Files.writeString(realFile, "public class RealClass {}");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());

            searchEngine.buildIndex();

            List<String> allFiles = searchEngine.search("public class", 100).stream()
                .map(r -> r.filePath)
                .toList();

            for (String filePath : allFiles) {
                for (String ignoredDir : ignoredDirs) {
                    assertFalse(filePath.contains(ignoredDir),
                        ignoredDir + " 目录下的文件不应被索引: " + filePath);
                }
            }

        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("鲁棒性 - 搜索单字符关键词不崩溃")
    void testSingleCharacterSearch() {
        assertDoesNotThrow(() -> {
            var results = searchEngine.search("a", 10);
            assertNotNull(results);
        });
    }

    @Test
    @DisplayName("鲁棒性 - 多次 buildIndex 不报错")
    void testMultipleBuildIndex() {
        assertDoesNotThrow(() -> {
            searchEngine.buildIndex();
            searchEngine.buildIndex();
            searchEngine.buildIndex();
        });
    }

    @Test
    @DisplayName("边界 - SearchEngineUtils.isCodeFile 各种扩展名")
    void testIsCodeFileVariousExtensions() {
        assertTrue(SearchEngineUtils.isCodeFile(Path.of("Test.java")));
        assertTrue(SearchEngineUtils.isCodeFile(Path.of("test.py")));
        assertTrue(SearchEngineUtils.isCodeFile(Path.of("test.js")));
        assertTrue(SearchEngineUtils.isCodeFile(Path.of("test.ts")));
        assertTrue(SearchEngineUtils.isCodeFile(Path.of("test.md")));
        assertTrue(SearchEngineUtils.isCodeFile(Path.of("test.json")));
        assertFalse(SearchEngineUtils.isCodeFile(Path.of("test.exe")));
        assertFalse(SearchEngineUtils.isCodeFile(Path.of("test.bin")));
    }

    @Test
    @DisplayName("边界 - SearchEngineUtils.isNotInIgnoredDir")
    void testIsNotInIgnoredDir() {
        assertTrue(SearchEngineUtils.isNotInIgnoredDir(Path.of("src/Test.java")));
        assertTrue(SearchEngineUtils.isNotInIgnoredDir(Path.of("src/main/java/Test.java")));
        assertTrue(SearchEngineUtils.isNotInIgnoredDir(Path.of("project/node_modules/file.java")) == false
            || SearchEngineUtils.isNotInIgnoredDir(Path.of("node_modules/package.json")) == false,
            "忽略目录应能被检测");
    }
}
