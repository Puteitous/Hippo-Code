package com.example.agent.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EditFailureLoggerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        EditFailureLogger.resetForTest();
        EditFailureLogger.setLogDirForTest(tempDir);
    }

    @AfterEach
    void tearDown() {
        EditFailureLogger.resetForTest();
    }

    @Test
    void testLogFailureCreatesFile() throws Exception {
        EditFailureLogger.logFailure(
            "src/main/Test.java", 50, 3, 60, 4,
            true, 30, "line_level"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        assertTrue(Files.exists(logFile));
    }

    @Test
    void testLogFailureJsonFormat() throws Exception {
        EditFailureLogger.logFailure(
            "src/main/Test.java", 50, 3, 60, 4,
            true, 30, "line_level"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        String content = Files.readString(logFile, StandardCharsets.UTF_8).trim();

        assertTrue(content.contains("\"filePath\":\"src/main/Test.java\""));
        assertTrue(content.contains("\"fileExtension\":\".java\""));
        assertTrue(content.contains("\"oldTextLength\":50"));
        assertTrue(content.contains("\"oldTextLineCount\":3"));
        assertTrue(content.contains("\"newTextLength\":60"));
        assertTrue(content.contains("\"newTextLineCount\":4"));
        assertTrue(content.contains("\"hasPartialMatch\":true"));
        assertTrue(content.contains("\"partialMatchLength\":30"));
        assertTrue(content.contains("\"diagnosticBranch\":\"line_level\""));
        assertTrue(content.contains("\"timestamp\":"));
    }

    @Test
    void testMultipleFailuresAreAppended() throws Exception {
        EditFailureLogger.logFailure(
            "file1.java", 10, 1, 20, 1,
            true, 5, "line_level"
        );
        EditFailureLogger.logFailure(
            "file2.py", 100, 5, 0, 0,
            false, 0, "extreme"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        String content = Files.readString(logFile, StandardCharsets.UTF_8).trim();
        String[] lines = content.split("\n");

        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("file1.java"));
        assertTrue(lines[1].contains("file2.py"));
    }

    @Test
    void testFileExtensionExtraction() throws Exception {
        EditFailureLogger.logFailure(
            "/path/to/MyComponent.tsx", 30, 2, 40, 3,
            false, 0, "extreme"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        String content = Files.readString(logFile, StandardCharsets.UTF_8);

        assertTrue(content.contains("\"fileExtension\":\".tsx\""));
    }

    @Test
    void testFileWithoutExtension() throws Exception {
        EditFailureLogger.logFailure(
            "Makefile", 20, 2, 25, 3,
            true, 15, "line_level"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        String content = Files.readString(logFile, StandardCharsets.UTF_8);

        assertTrue(content.contains("\"fileExtension\":\"\""));
    }

    @Test
    void testSpecialCharactersInPath() throws Exception {
        EditFailureLogger.logFailure(
            "src/test/file \"quote\".java", 10, 1, 15, 1,
            false, 0, "extreme"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        String content = Files.readString(logFile, StandardCharsets.UTF_8);

        assertTrue(content.contains("\\\"quote\\\""));
    }

    @Test
    void testResetForTestClearsOverride() {
        EditFailureLogger.resetForTest();
        EditFailureLogger.setLogDirForTest(tempDir);
        assertNotNull(tempDir);

        EditFailureLogger.resetForTest();

        EditFailureLogger.logFailure(
            "test.txt", 5, 1, 10, 1,
            false, 0, "extreme"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        assertFalse(Files.exists(logFile), "reset 后应不再写入 tempDir");
    }

    @Test
    void testExtremeBranchLogging() throws Exception {
        EditFailureLogger.logFailure(
            "data.json", 200, 10, 0, 0,
            false, 0, "extreme"
        );

        Path logFile = tempDir.resolve("edit-failures.jsonl");
        String content = Files.readString(logFile, StandardCharsets.UTF_8);

        assertTrue(content.contains("\"hasPartialMatch\":false"));
        assertTrue(content.contains("\"partialMatchLength\":0"));
        assertTrue(content.contains("\"diagnosticBranch\":\"extreme\""));
    }
}
