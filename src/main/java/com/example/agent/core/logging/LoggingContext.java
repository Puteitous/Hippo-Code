package com.example.agent.core.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public final class LoggingContext implements AutoCloseable {

    public static final String SESSION_ID = "sessionId";
    public static final String TURN = "turn";
    public static final String TOOL = "tool";

    private LoggingContext() {}

    public static LoggingContext create() {
        return new LoggingContext();
    }

    public LoggingContext session(String sessionId) {
        if (sessionId != null) {
            MDC.put(SESSION_ID, shorten(sessionId, 12));
        }
        return this;
    }

    public LoggingContext turn(int turnNumber) {
        MDC.put(TURN, String.valueOf(turnNumber));
        return this;
    }

    public static void setTool(String toolName) {
        if (toolName != null) {
            MDC.put(TOOL, toolName);
        }
    }

    public static void clearTool() {
        MDC.remove(TOOL);
    }

    public static void clearAll() {
        MDC.remove(SESSION_ID);
        MDC.remove(TURN);
        MDC.remove(TOOL);
    }

    public static Map<String, String> snapshot() {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        return copy != null ? copy : new HashMap<>();
    }

    public static void restore(Map<String, String> snapshot) {
        if (snapshot != null && !snapshot.isEmpty()) {
            MDC.setContextMap(snapshot);
        }
    }

    @Override
    public void close() {
        clearAll();
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
