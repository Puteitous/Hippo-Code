package com.example.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Test error message";
        ToolExecutionException exception = new ToolExecutionException(message);
        
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Test error message";
        Throwable cause = new RuntimeException("Original cause");
        ToolExecutionException exception = new ToolExecutionException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testConstructorWithNullMessage() {
        ToolExecutionException exception = new ToolExecutionException(null);
        
        assertNull(exception.getMessage());
    }

    @Test
    void testConstructorWithEmptyMessage() {
        String message = "";
        ToolExecutionException exception = new ToolExecutionException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testConstructorWithNullCause() {
        String message = "Test error";
        ToolExecutionException exception = new ToolExecutionException(message, null);
        
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionIsThrowable() {
        ToolExecutionException exception = new ToolExecutionException("test");
        
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }

    @Test
    void testExceptionCanBeThrown() {
        assertThrows(ToolExecutionException.class, () -> {
            throw new ToolExecutionException("test");
        });
    }

    @Test
    void testExceptionCanBeCaught() {
        boolean caught = false;
        try {
            throw new ToolExecutionException("test");
        } catch (ToolExecutionException e) {
            assertEquals("test", e.getMessage());
            caught = true;
        }
        assertTrue(caught, "Exception should have been caught");
    }

    @Test
    void testExceptionChaining() {
        Exception original = new IllegalArgumentException("original");
        ToolExecutionException wrapped = new ToolExecutionException("wrapped", original);
        
        assertEquals("wrapped", wrapped.getMessage());
        assertEquals(original, wrapped.getCause());
        assertEquals("original", wrapped.getCause().getMessage());
    }
}