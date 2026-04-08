package com.example.agent.session;

import com.example.agent.llm.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

    @Test
    void testJsonSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        List<Message> messages = Arrays.asList(
            Message.system("System prompt"),
            Message.user("Hello"),
            Message.assistant("Hi there!")
        );
        
        SessionData original = SessionData.create("json-test", messages, SessionData.Status.ACTIVE);
        original.setCreatedAt(LocalDateTime.of(2024, 1, 1, 12, 0, 0));
        original.setLastActiveAt(LocalDateTime.of(2024, 1, 1, 12, 30, 0));
        
        String json = mapper.writeValueAsString(original);
        
        assertNotNull(json);
        assertTrue(json.contains("json-test"));
        assertTrue(json.contains("ACTIVE"));
        
        SessionData deserialized = mapper.readValue(json, SessionData.class);
        
        assertEquals(original.getSessionId(), deserialized.getSessionId());
        assertEquals(original.getStatus(), deserialized.getStatus());
        assertEquals(original.getMessageCount(), deserialized.getMessageCount());
        assertEquals(original.getLastUserMessage(), deserialized.getLastUserMessage());
        assertEquals(original.getCreatedAt(), deserialized.getCreatedAt());
        assertEquals(original.getLastActiveAt(), deserialized.getLastActiveAt());
    }

    @Test
    void testJsonSerializationWithNullFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        SessionData session = new SessionData();
        session.setSessionId("null-fields-test");
        session.setStatus(SessionData.Status.INTERRUPTED);
        
        String json = mapper.writeValueAsString(session);
        SessionData deserialized = mapper.readValue(json, SessionData.class);
        
        assertEquals("null-fields-test", deserialized.getSessionId());
        assertEquals(SessionData.Status.INTERRUPTED, deserialized.getStatus());
        assertNotNull(deserialized.getMessages());
        assertTrue(deserialized.getMessages().isEmpty());
    }

    @Test
    void testJsonDeserializationWithUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        String json = "{\"sessionId\":\"unknown-test\",\"status\":\"ACTIVE\",\"unknownField\":\"value\"}";
        
        SessionData session = mapper.readValue(json, SessionData.class);
        
        assertEquals("unknown-test", session.getSessionId());
        assertEquals(SessionData.Status.ACTIVE, session.getStatus());
    }

    @Test
    void testMessageWithNullContent() {
        Message msg = Message.user("Test");
        msg.setContent(null);
        
        List<Message> messages = Arrays.asList(msg);
        SessionData session = SessionData.create("null-content", messages, SessionData.Status.ACTIVE);
        
        assertNull(session.getLastUserMessage());
    }

    @Test
    void testMessageWithNullRole() {
        Message msg = new Message();
        msg.setRole(null);
        msg.setContent("Test content");
        
        List<Message> messages = Arrays.asList(msg);
        SessionData session = SessionData.create("null-role", messages, SessionData.Status.ACTIVE);
        
        assertNull(session.getLastUserMessage());
    }

    @Test
    void testMessageWithEmptyRole() {
        Message msg = new Message();
        msg.setRole("");
        msg.setContent("Test content");
        
        List<Message> messages = Arrays.asList(msg);
        SessionData session = SessionData.create("empty-role", messages, SessionData.Status.ACTIVE);
        
        assertNull(session.getLastUserMessage());
    }

    @Test
    void testExtractLastUserMessageWithMixedMessages() {
        List<Message> messages = Arrays.asList(
            Message.system("System"),
            Message.user("First user"),
            Message.assistant("Response 1"),
            Message.user("Second user"),
            Message.assistant("Response 2"),
            Message.user("Third user"),
            Message.assistant("Response 3")
        );
        
        SessionData session = SessionData.create("mixed", messages, SessionData.Status.ACTIVE);
        assertEquals("Third user", session.getLastUserMessage());
    }

    @Test
    void testSetMessagesUpdatesMessageCount() {
        SessionData session = new SessionData();
        assertEquals(0, session.getMessageCount());
        
        session.setMessages(Arrays.asList(Message.user("Test1")));
        assertEquals(1, session.getMessageCount());
        
        session.setMessages(Arrays.asList(Message.user("Test1"), Message.user("Test2")));
        assertEquals(2, session.getMessageCount());
        
        session.setMessages(null);
        assertEquals(0, session.getMessageCount());
    }

    @Test
    void testSetMessagesUpdatesLastUserMessage() {
        SessionData session = new SessionData();
        assertNull(session.getLastUserMessage());
        
        session.setMessages(Arrays.asList(Message.user("First")));
        assertEquals("First", session.getLastUserMessage());
        
        session.setMessages(Arrays.asList(Message.user("Second")));
        assertEquals("Second", session.getLastUserMessage());
    }

    @Test
    void testStatusTransitions() {
        SessionData session = new SessionData();
        assertEquals(SessionData.Status.ACTIVE, session.getStatus());
        
        session.setStatus(SessionData.Status.INTERRUPTED);
        assertEquals(SessionData.Status.INTERRUPTED, session.getStatus());
        assertTrue(session.canResume());
        assertTrue(session.isActive());
        
        session.setStatus(SessionData.Status.COMPLETED);
        assertEquals(SessionData.Status.COMPLETED, session.getStatus());
        assertFalse(session.canResume());
        assertFalse(session.isActive());
    }

    @Test
    void testConstructorWithSessionId() {
        SessionData session = new SessionData("custom-id");
        
        assertEquals("custom-id", session.getSessionId());
        assertEquals(SessionData.Status.ACTIVE, session.getStatus());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastActiveAt());
    }

    @Test
    void testDefaultConstructor() {
        SessionData session = new SessionData();
        
        assertNull(session.getSessionId());
        assertEquals(SessionData.Status.ACTIVE, session.getStatus());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getLastActiveAt());
    }

    @Test
    void testLargeMessageList() {
        List<Message> messages = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            messages.add(Message.user("Message " + i));
        }
        
        SessionData session = SessionData.create("large", messages, SessionData.Status.ACTIVE);
        
        assertEquals(1000, session.getMessageCount());
        assertEquals("Message 999", session.getLastUserMessage());
    }

    @Test
    void testExactly100CharacterMessage() {
        String exact100 = "x".repeat(100);
        List<Message> messages = Arrays.asList(Message.user(exact100));
        
        SessionData session = SessionData.create("exact-100", messages, SessionData.Status.ACTIVE);
        
        assertEquals(100, session.getLastUserMessage().length());
        assertFalse(session.getLastUserMessage().endsWith("..."));
    }

    @Test
    void test101CharacterMessage() {
        String content101 = "x".repeat(101);
        List<Message> messages = Arrays.asList(Message.user(content101));
        
        SessionData session = SessionData.create("101-chars", messages, SessionData.Status.ACTIVE);
        
        assertEquals(103, session.getLastUserMessage().length());
        assertTrue(session.getLastUserMessage().endsWith("..."));
    }

    @Test
    void testToString() {
        SessionData session = new SessionData("tostring-test");
        session.setStatus(SessionData.Status.INTERRUPTED);
        session.setMessages(Arrays.asList(Message.user("Test")));
        
        String str = session.toString();
        
        assertTrue(str.contains("tostring-test"));
        assertTrue(str.contains("INTERRUPTED"));
        assertTrue(str.contains("messageCount=1"));
    }
}
