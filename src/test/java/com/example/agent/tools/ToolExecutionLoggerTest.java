package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionLoggerTest {

    private ToolExecutionLogger logger;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        logger = new ToolExecutionLogger();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testLogSuccess() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("test", "value");

        assertDoesNotThrow(() -> {
            logger.log("test_tool", args, "result", 100, true);
        });
    }

    @Test
    void testLogFailure() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log("test_tool", args, null, 50, false);
        });
    }

    @Test
    void testLogWithNullToolName() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log(null, args, "result", 100, true);
        });
    }

    @Test
    void testLogWithEmptyToolName() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log("", args, "result", 100, true);
        });
    }

    @Test
    void testLogWithWhitespaceToolName() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log("   ", args, "result", 100, true);
        });
    }

    @Test
    void testLogWithNullArguments() {
        assertDoesNotThrow(() -> {
            logger.log("test_tool", null, "result", 100, true);
        });
    }

    @Test
    void testLogWithNullResult() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log("test_tool", args, null, 100, true);
        });
    }

    @Test
    void testLogWithZeroDuration() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log("test_tool", args, "result", 0, true);
        });
    }

    @Test
    void testLogWithNegativeDuration() {
        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            logger.log("test_tool", args, "result", -1, true);
        });
    }

    @Test
    void testGetMetrics() {
        ObjectNode args = objectMapper.createObjectNode();

        logger.log("test_tool", args, "result", 100, true);
        logger.log("test_tool", args, "result", 200, true);
        logger.log("test_tool", args, null, 50, false);

        var metrics = logger.getMetrics("test_tool");

        assertNotNull(metrics);
        assertEquals(3, metrics.getCallCount());
        assertEquals(2, metrics.getSuccessCount());
        assertEquals(1, metrics.getFailureCount());
        assertEquals(116.67, metrics.getAverageDuration(), 0.1);
    }

    @Test
    void testGetMetricsForUnknownTool() {
        var metrics = logger.getMetrics("unknown_tool");

        assertNull(metrics);
    }

    @Test
    void testPrintSummary() {
        ObjectNode args = objectMapper.createObjectNode();

        logger.log("tool1", args, "result", 100, true);
        logger.log("tool2", args, "result", 200, false);

        assertDoesNotThrow(() -> {
            logger.printSummary();
        });
    }

    @Test
    void testFileLoggingEnabled() {
        Path logFile = tempDir.resolve("test.log");
        ToolExecutionLogger fileLogger = new ToolExecutionLogger(logFile, true);

        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            fileLogger.log("test_tool", args, "result", 100, true);
        });
    }

    @Test
    void testFileLoggingWithInvalidPath() {
        Path invalidPath = Path.of("/non/existent/path/test.log");
        ToolExecutionLogger fileLogger = new ToolExecutionLogger(invalidPath, true);

        ObjectNode args = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> {
            fileLogger.log("test_tool", args, "result", 100, true);
        });
    }

    @Test
    void testToolMetricsSuccessRate() {
        var metrics = new ToolExecutionLogger.ToolMetrics("test");

        metrics.record(100, true);
        metrics.record(100, true);
        metrics.record(100, false);

        assertEquals(3, metrics.getCallCount());
        assertEquals(2, metrics.getSuccessCount());
        assertEquals(1, metrics.getFailureCount());
        assertEquals(2.0 / 3.0, metrics.getSuccessRate(), 0.01);
    }

    @Test
    void testToolMetricsEmptyMetrics() {
        var metrics = new ToolExecutionLogger.ToolMetrics("test");

        assertEquals(0, metrics.getCallCount());
        assertEquals(0, metrics.getSuccessCount());
        assertEquals(0, metrics.getFailureCount());
        assertEquals(0, metrics.getTotalDuration());
        assertEquals(0, metrics.getAverageDuration(), 0.01);
        assertEquals(0, metrics.getSuccessRate(), 0.01);
    }
}
