package com.example.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryToolSandbox 单元测试
 * 
 * 测试工具权限沙箱的安全性和鲁棒性
 */
class MemoryToolSandboxTest {

    @TempDir
    Path tempDir;

    private MemoryToolSandbox sandbox;
    private Path memoryRoot;

    @BeforeEach
    void setUp() {
        memoryRoot = tempDir.resolve(".hippo").resolve("memory");
        sandbox = new MemoryToolSandbox(memoryRoot);
    }

    @Test
    void testAllowReadFile() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "/any/path/file.txt");
        
        MemoryPermissionResult result = sandbox.check("read_file", input);
        assertTrue(result.isAllowed());
    }

    @Test
    void testAllowWriteFileInMemoryDir() {
        Map<String, Object> input = new HashMap<>();
        String validPath = memoryRoot.resolve("test.md").toString();
        input.put("file_path", validPath);
        
        MemoryPermissionResult result = sandbox.check("write_file", input);
        assertTrue(result.isAllowed());
    }

    @Test
    void testDenyWriteFileOutsideMemoryDir() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "/etc/passwd");
        
        MemoryPermissionResult result = sandbox.check("write_file", input);
        assertFalse(result.isAllowed());
        assertTrue(result.getMessage().contains("只能写入 memory 目录"));
    }

    @Test
    void testDenyEditFileOutsideMemoryDir() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "/home/user/project/src/Main.java");
        
        MemoryPermissionResult result = sandbox.check("edit_file", input);
        assertFalse(result.isAllowed());
    }

    @Test
    void testAllowGlobAndGrep() {
        Map<String, Object> input = new HashMap<>();
        input.put("pattern", "*.java");
        
        MemoryPermissionResult globResult = sandbox.check("glob", input);
        assertTrue(globResult.isAllowed());
        
        MemoryPermissionResult grepResult = sandbox.check("grep", input);
        assertTrue(grepResult.isAllowed());
    }

    @Test
    void testAllowSafeBashCommands() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "ls -la");
        
        MemoryPermissionResult result = sandbox.check("bash", input);
        assertTrue(result.isAllowed());
        
        input.put("command", "cat file.txt");
        result = sandbox.check("bash", input);
        assertTrue(result.isAllowed());
        
        input.put("command", "grep 'pattern' file.txt");
        result = sandbox.check("bash", input);
        assertTrue(result.isAllowed());
    }

    @Test
    void testDenyDangerousBashCommands() {
        Map<String, Object> input = new HashMap<>();
        
        // 写操作
        input.put("command", "rm -rf /tmp/*");
        MemoryPermissionResult result = sandbox.check("bash", input);
        assertFalse(result.isAllowed());
        
        // 重定向
        input.put("command", "echo 'x' > file.txt");
        result = sandbox.check("bash", input);
        assertFalse(result.isAllowed());
        
        // 管道
        input.put("command", "cat file | tee out.txt");
        result = sandbox.check("bash", input);
        assertFalse(result.isAllowed());
    }

    @Test
    void testPathTraversalAttack() {
        // 路径遍历攻击尝试
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "../../../etc/passwd");
        
        MemoryPermissionResult result = sandbox.check("write_file", input);
        assertFalse(result.isAllowed());
        
        // 另一种遍历方式
        input.put("file_path", memoryRoot.toString() + "/../../../etc/passwd");
        result = sandbox.check("write_file", input);
        assertFalse(result.isAllowed());
    }

    @Test
    void testAbsoluteOutsidePath() {
        Map<String, Object> input = new HashMap<>();
        input.put("file_path", "/etc/passwd");
        
        MemoryPermissionResult result = sandbox.check("write_file", input);
        assertFalse(result.isAllowed());
    }

    @Test
    void testNullAndEmptyInputs() {
        // null 输入
        MemoryPermissionResult result = sandbox.check("write_file", null);
        assertFalse(result.isAllowed());
        assertTrue(result.getMessage().contains("文件路径为空"));
        
        // 空输入
        result = sandbox.check("write_file", new HashMap<>());
        assertFalse(result.isAllowed());
    }

    @Test
    void testNullBashCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", null);
        
        MemoryPermissionResult result = sandbox.check("bash", input);
        assertFalse(result.isAllowed());
        assertTrue(result.getMessage().contains("Bash 命令为空"));
    }

    @Test
    void testEmptyBashCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "");
        
        MemoryPermissionResult result = sandbox.check("bash", input);
        assertFalse(result.isAllowed());
    }

    @Test
    void testUnsupportedTool() {
        Map<String, Object> input = new HashMap<>();
        
        MemoryPermissionResult result = sandbox.check("unknown_tool", input);
        assertFalse(result.isAllowed());
        assertTrue(result.getMessage().contains("不支持的工具"));
    }

    @Test
    void testAlternativePathKey() {
        // 测试使用 "path" 而不是 "file_path"
        Map<String, Object> input = new HashMap<>();
        String validPath = memoryRoot.resolve("test.md").toString();
        input.put("path", validPath);
        
        MemoryPermissionResult result = sandbox.check("write_file", input);
        assertTrue(result.isAllowed());
    }

    @Test
    void testComplexPathTraversalAttacks() {
        // 各种路径遍历攻击变体
        String[] attackPaths = {
            "../../../etc/passwd",
            "..\\..\\..\\etc\\passwd",
            memoryRoot + "/../../outside.txt",
            "/tmp/../../../etc/passwd",
            "....//....//etc/passwd"
        };
        
        for (String attackPath : attackPaths) {
            Map<String, Object> input = new HashMap<>();
            input.put("file_path", attackPath);
            
            MemoryPermissionResult result = sandbox.check("write_file", input);
            assertFalse(result.isAllowed(), "攻击路径应该被拒绝：" + attackPath);
        }
    }

    @Test
    void testUnicodePathTraversalAttacks() {
        // Unicode 编码绕过测试
        String[] unicodeAttacks = {
            "%2e%2e%2f%2e%2e%2fetc/passwd",  // URL 编码的 ../
            "%2e%2e/%2e%2e/etc/passwd",      // 混合编码
            "..%2f..%2fetc/passwd",          // 部分编码
            "%2e%2e\\%2e%2e\\etc\\passwd"    // Windows 风格编码
        };
        
        for (String attackPath : unicodeAttacks) {
            Map<String, Object> input = new HashMap<>();
            input.put("file_path", attackPath);
            
            MemoryPermissionResult result = sandbox.check("write_file", input);
            // Java 的 Paths.get() 会自动解码 URL 编码，normalize() 会处理
            // 但我们需要确保即使解码后也被拒绝
            assertFalse(result.isAllowed(), "Unicode 攻击路径应该被拒绝：" + attackPath);
        }
    }

    @Test
    void testMixedEncodingAttacks() {
        // 混合编码和特殊字符攻击
        String[] attacks = {
            memoryRoot + "/..%2f..%2f..%2fetc/passwd",
            memoryRoot + "/test/../../../etc/passwd",
            memoryRoot + "\\..\\..\\..\\etc\\passwd"
        };
        
        for (String attackPath : attacks) {
            Map<String, Object> input = new HashMap<>();
            input.put("file_path", attackPath);
            
            MemoryPermissionResult result = sandbox.check("write_file", input);
            assertFalse(result.isAllowed(), "混合编码攻击应该被拒绝：" + attackPath);
        }
    }

    @Test
    void testBashGitCommands() {
        // Git 命令应该被拒绝
        String[] gitCommands = {
            "git status",
            "git commit -m 'test'",
            "git branch -D temp",
            "git reset --hard"
        };
        
        for (String command : gitCommands) {
            Map<String, Object> input = new HashMap<>();
            input.put("command", command);
            
            MemoryPermissionResult result = sandbox.check("bash", input);
            assertFalse(result.isAllowed(), "Git 命令应该被拒绝：" + command);
        }
    }

    @Test
    void testGetMemoryRoot() {
        assertEquals(memoryRoot, sandbox.getMemoryRoot());
    }

    @Test
    void testPermissionResultToString() {
        MemoryPermissionResult allow = MemoryPermissionResult.allow();
        assertEquals("ALLOWED", allow.toString());
        
        MemoryPermissionResult deny = MemoryPermissionResult.deny("test reason");
        assertTrue(deny.toString().contains("DENIED"));
        assertTrue(deny.toString().contains("test reason"));
    }
}
