package com.example.agent.session;

import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionDataTest {

    @Test
    void testCreateSessionData() {
        List<Message> messages = Arrays.asList(
            Message.system("System prompt"),
            Message.user("Hello"),
            Message.assistant("Hi!")
        );

        SessionData session = SessionData.create("test-id", messages, SessionData.Status.ACTIVE);

        assertEquals("test-id", session.getSessionId());
        assertEquals(3, session.getMessageCount());
        assertEquals(SessionData.Status.ACTIVE, session.getStatus());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastActiveAt());
        assertEquals("Hello", session.getLastUserMessage());
    }

    @Test
    void testExtractLastUserMessage() {
        List<Message> messages = Arrays.asList(
            Message.system("System"),
            Message.user("First message"),
            Message.assistant("Response"),
            Message.user("Last user message")
        );

        SessionData session = SessionData.create("test", messages, SessionData.Status.ACTIVE);
        assertEquals("Last user message", session.getLastUserMessage());
    }

    @Test
    void testExtractLastUserMessageWithLongContent() {
        String longContent = "x".repeat(150);
        List<Message> messages = Arrays.asList(Message.user(longContent));

        SessionData session = SessionData.create("test", messages, SessionData.Status.ACTIVE);
        assertTrue(session.getLastUserMessage().endsWith("..."));
        assertEquals(103, session.getLastUserMessage().length());
    }

    @Test
    void testCanResume() {
        SessionData active = new SessionData();
        active.setStatus(SessionData.Status.ACTIVE);
        assertTrue(active.canResume());

        SessionData interrupted = new SessionData();
        interrupted.setStatus(SessionData.Status.INTERRUPTED);
        assertTrue(interrupted.canResume());

        SessionData completed = new SessionData();
        completed.setStatus(SessionData.Status.COMPLETED);
        assertFalse(completed.canResume());
    }

    @Test
    void testIsActive() {
        SessionData active = new SessionData();
        active.setStatus(SessionData.Status.ACTIVE);
        assertTrue(active.isActive());

        SessionData interrupted = new SessionData();
        interrupted.setStatus(SessionData.Status.INTERRUPTED);
        assertTrue(interrupted.isActive());

        SessionData completed = new SessionData();
        completed.setStatus(SessionData.Status.COMPLETED);
        assertFalse(completed.isActive());
    }

    @Test
    void testTouch() {
        SessionData session = new SessionData();
        LocalDateTime before = session.getLastActiveAt();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        session.touch();
        LocalDateTime after = session.getLastActiveAt();
        
        assertTrue(after.isAfter(before) || after.isEqual(before));
    }

    @Test
    void testSetMessages() {
        SessionData session = new SessionData();
        session.setMessages(Arrays.asList(Message.user("Test")));
        
        assertEquals(1, session.getMessageCount());
        assertEquals(1, session.getMessages().size());
    }

    @Test
    void testSetNullMessages() {
        SessionData session = new SessionData();
        session.setMessages(null);
        
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
        assertEquals(0, session.getMessageCount());
    }

    @Test
    void testEmptyMessages() {
        SessionData session = SessionData.create("test", Collections.emptyList(), SessionData.Status.ACTIVE);
        
        assertEquals(0, session.getMessageCount());
        assertNull(session.getLastUserMessage());
    }

    @Test
    void testNoUserMessage() {
        List<Message> messages = Arrays.asList(
            Message.system("System"),
            Message.assistant("Response")
        );

        SessionData session = SessionData.create("test", messages, SessionData.Status.ACTIVE);
        assertNull(session.getLastUserMessage());
    }
}
