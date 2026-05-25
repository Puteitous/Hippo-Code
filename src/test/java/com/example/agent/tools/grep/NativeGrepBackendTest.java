package com.example.agent.tools.grep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeGrepBackendTest {

    private NativeGrepBackend backend;
    private JavaGrepBackend javaBackend;
    private Path testDir;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        backend = new NativeGrepBackend();
        javaBackend = new JavaGrepBackend();
        // 在项目目录内创建测试子目录，避免被 PathSecurityUtils 过滤
        testDir = Files.createTempDirectory(Path.of("target"), "test-");
    }

    @Test
    void testNativeBackendAvailable() {
        assertTrue(backend.isAvailable(), "NativeGrepBackend 应该可用（ripgrep 已打包）");
    }

    @Test
    void testNativeBackendBasicSearch() throws Exception {
        createTestFile("test.txt", "hello world\nfoo bar\nhello again");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("hello")
            .searchPath(tempDir)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertEquals(2, results.size(), "应该找到 2 个匹配项");
        
        SearchResult first = results.get(0);
        assertTrue(first.getFilePath().endsWith("test.txt"), "文件路径应该以 test.txt 结尾");
        assertEquals(1, first.getLineNumber());
        assertTrue(first.getLineContent().contains("hello"));
    }

    @Test
    void testNativeBackendCaseInsensitive() throws Exception {
        createTestFile("case.txt", "Hello World\nHELLO AGAIN\nhello third");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("HELLO")
            .caseSensitive(false)
            .searchPath(tempDir)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertEquals(3, results.size(), "不区分大小写应该找到 3 个匹配项");
    }

    @Test
    void testNativeBackendCaseSensitive() throws Exception {
        createTestFile("case.txt", "Hello World\nhello again\nHELLO THIRD");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("HELLO")
            .caseSensitive(true)
            .searchPath(tempDir)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertEquals(1, results.size(), "区分大小写应该只找到 1 个匹配项");
        assertEquals(3, results.get(0).getLineNumber());
    }

    @Test
    void testNativeBackendMaxResults() throws Exception {
        createTestFile("multi.txt", "match\nmatch\nmatch\nmatch\nmatch");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("match")
            .searchPath(tempDir)
            .maxResults(3)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertTrue(results.size() <= 3, "结果数不应该超过 maxResults 限制");
    }

    @Test
    void testNativeBackendFilePattern() throws Exception {
        createTestFile("test.java", "public class Test {}");
        createTestFile("test.txt", "public class NotJava");
        createTestFile("ignore.md", "public class Ignored");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("public class")
            .filePattern("*.java")
            .searchPath(tempDir)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertEquals(1, results.size(), "应该只找到.java 文件");
        assertTrue(results.get(0).getFilePath().endsWith("test.java"), "应该找到 test.java 文件");
    }

    @Test
    void testNativeBackendContextLines() throws Exception {
        String content = """
            line one
            line two with target
            line three
            line four
            """;
        createTestFile("context.txt", content);
        
        GrepOptions options = GrepOptions.builder()
            .pattern("target")
            .contextBefore(1)
            .contextAfter(1)
            .searchPath(tempDir)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertFalse(results.isEmpty(), "应该找到匹配项");
    }

    @Test
    void testNativeBackendNoResults() throws Exception {
        createTestFile("empty.txt", "nothing here\nno matches\nblank");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("xyz123notfound")
            .searchPath(tempDir)
            .build();
        
        List<SearchResult> results = backend.search(options);
        
        assertNotNull(results);
        assertTrue(results.isEmpty(), "找不到匹配项应该返回空列表");
    }

    @Test
    void testNativeBackendPathValidation() throws Exception {
        Path nonExistentPath = tempDir.resolve("does_not_exist");
        
        GrepOptions options = GrepOptions.builder()
            .pattern("test")
            .searchPath(nonExistentPath)
            .build();
        
        assertThrows(Exception.class, () -> {
            backend.search(options);
        }, "搜索不存在的路径应该抛出异常");
    }

    @Test
    void testNativeVsJavaConsistency() throws Exception {
        createTestFile("consistency.txt", """
            public class ConsistencyTest {
                public static void main(String[] args) {
                    System.out.println("test");
                }
            }
            """);
        
        GrepOptions options = GrepOptions.builder()
            .pattern("class")
            .caseSensitive(false)
            .searchPath(tempDir)
            .maxResults(10)
            .build();
        
        List<SearchResult> nativeResults = backend.search(options);
        JavaGrepBackend javaBackend = new JavaGrepBackend();
        List<SearchResult> javaResults = javaBackend.search(options);
        
        assertNotNull(nativeResults);
        assertNotNull(javaResults);
        
        if (nativeResults.size() != javaResults.size()) {
            System.out.println("Native 结果数：" + nativeResults.size());
            System.out.println("Java 结果数：" + javaResults.size());
            System.out.println("Native 结果：");
            nativeResults.forEach(r -> System.out.println("  " + r));
            System.out.println("Java 结果：");
            javaResults.forEach(r -> System.out.println("  " + r));
        }
        
        assertEquals(
            nativeResults.size(), 
            javaResults.size(), 
            "Native 和 Java 后端应该返回相同数量的结果"
        );
        
        for (int i = 0; i < nativeResults.size(); i++) {
            SearchResult nativeResult = nativeResults.get(i);
            SearchResult javaResult = javaResults.get(i);
            
            assertTrue(
                nativeResult.getFilePath().endsWith(javaResult.getFilePath().replaceAll("^.*[\\\\/]", "")),
                "第 " + i + " 个结果的文件路径应该相同"
            );
            assertEquals(
                nativeResult.getLineNumber(), 
                javaResult.getLineNumber(),
                "第 " + i + " 个结果的行号应该相同"
            );
        }
    }

    private void createTestFile(String filename, String content) throws IOException {
        Path filePath = tempDir.resolve(filename);
        Files.writeString(filePath, content);
    }
}
