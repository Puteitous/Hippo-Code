package com.example.agent.session;

import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionStorageTest {

    @TempDir
    Path tempDir;

    private SessionStorage storage;

    @BeforeEach
    void setUp() {
        storage = new SessionStorage(tempDir, 10);
    }

    @Test
    void testSaveAndLoadSession() {
        List<Message> messages = Arrays.asList(
            Message.system("You are a helpful assistant."),
            Message.user("Hello"),
            Message.assistant("Hi there!")
        );

        SessionData session = SessionData.create("test-session-1", messages, SessionData.Status.ACTIVE);

        SessionData saved = storage.saveSession(session);
        assertNotNull(saved);
        assertEquals("test-session-1", saved.getSessionId());

        Optional<SessionData> loaded = storage.loadSession("test-session-1");
        assertTrue(loaded.isPresent());
        assertEquals("test-session-1", loaded.get().getSessionId());
        assertEquals(3, loaded.get().getMessageCount());
        assertEquals(SessionData.Status.ACTIVE, loaded.get().getStatus());
    }

    @Test
    void testLoadNonExistentSession() {
        Optional<SessionData> loaded = storage.loadSession("non-existent");
        assertFalse(loaded.isPresent());
    }

    @Test
    void testListSessions() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData session1 = SessionData.create("session-1", messages, SessionData.Status.ACTIVE);
        SessionData session2 = SessionData.create("session-2", messages, SessionData.Status.INTERRUPTED);
        
        storage.saveSession(session1);
        storage.saveSession(session2);

        List<SessionData> sessions = storage.listSessions();
        assertEquals(2, sessions.size());
    }

    @Test
    void testListResumableSessions() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData active = SessionData.create("active", messages, SessionData.Status.ACTIVE);
        SessionData interrupted = SessionData.create("interrupted", messages, SessionData.Status.INTERRUPTED);
        SessionData completed = SessionData.create("completed", messages, SessionData.Status.COMPLETED);
        
        storage.saveSession(active);
        storage.saveSession(interrupted);
        storage.saveSession(completed);

        List<SessionData> resumable = storage.listResumableSessions();
        assertEquals(2, resumable.size());
        assertTrue(resumable.stream().allMatch(SessionData::canResume));
    }

    @Test
    void testFindLatestResumableSession() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData session1 = SessionData.create("older", messages, SessionData.Status.INTERRUPTED);
        session1.setLastActiveAt(LocalDateTime.now().minusHours(1));
        
        SessionData session2 = SessionData.create("newer", messages, SessionData.Status.INTERRUPTED);
        session2.setLastActiveAt(LocalDateTime.now());

        storage.saveSession(session1);
        storage.saveSession(session2);

        Optional<SessionData> latest = storage.findLatestResumableSession();
        assertTrue(latest.isPresent());
        assertEquals("newer", latest.get().getSessionId());
    }

    @Test
    void testDeleteSession() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        SessionData session = SessionData.create("to-delete", messages, SessionData.Status.ACTIVE);
        
        storage.saveSession(session);
        assertTrue(storage.loadSession("to-delete").isPresent());

        boolean deleted = storage.deleteSession("to-delete");
        assertTrue(deleted);
        assertFalse(storage.loadSession("to-delete").isPresent());
    }

    @Test
    void testCleanupOldSessions() {
        SessionStorage smallStorage = new SessionStorage(tempDir.resolve("cleanup"), 2);
        
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        for (int i = 0; i < 5; i++) {
            SessionData session = SessionData.create("session-" + i, messages, SessionData.Status.ACTIVE);
            session.setLastActiveAt(LocalDateTime.now().minusHours(5 - i));
            smallStorage.saveSession(session);
        }

        List<SessionData> remaining = smallStorage.listSessions();
        assertTrue(remaining.size() <= 2);
    }

    @Test
    void testSaveNullSession() {
        SessionData result = storage.saveSession(null);
        assertNull(result);
    }
}
