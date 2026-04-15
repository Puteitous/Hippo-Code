package com.example.agent.orchestrator.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompilationCheckerTest {

    @Test
    void check_shouldPassForValidProject() {
        CompilationChecker checker = new CompilationChecker();

        CompilationChecker.CompilationResult result = checker.check();

        assertTrue(result.isSuccess(), "项目应该能编译通过");
    }

    @Test
    void formatErrorMessage_shouldFormatErrors() {
        CompilationChecker.CompilationResult success =
                new CompilationChecker.CompilationResult(true, null, "");

        assertEquals("", success.formatErrorMessage());

        CompilationChecker.CompilationResult failure =
                new CompilationChecker.CompilationResult(false,
                        java.util.List.of("Test.java:10:20 - 语法错误"),
                        "raw output");

        String message = failure.formatErrorMessage();
        assertTrue(message.contains("编译失败"));
        assertTrue(message.contains("已自动回滚"));
        assertTrue(message.contains("Test.java:10:20"));
        assertTrue(message.contains("修复"));
    }
}
