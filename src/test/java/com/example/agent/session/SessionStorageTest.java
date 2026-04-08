package com.example.agent.session;

import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        SessionData ignored = SessionData.create("ignored", messages, SessionData.Status.IGNORED);
        
        storage.saveSession(active);
        storage.saveSession(interrupted);
        storage.saveSession(completed);
        storage.saveSession(ignored);

        List<SessionData> resumable = storage.listResumableSessions();
        assertEquals(1, resumable.size());
        assertEquals("interrupted", resumable.get(0).getSessionId());
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

    @Test
    void testConcurrentSaveSessions() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    List<Message> messages = Arrays.asList(Message.user("Test " + index));
                    SessionData session = SessionData.create("concurrent-" + index, messages, SessionData.Status.ACTIVE);
                    SessionData saved = storage.saveSession(session);
                    if (saved != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(threadCount, successCount.get());
        List<SessionData> sessions = storage.listSessions();
        assertEquals(threadCount, sessions.size());
    }

    @Test
    void testConcurrentReadWriteSessions() throws InterruptedException {
        List<Message> messages = Arrays.asList(Message.user("Initial"));
        SessionData session = SessionData.create("concurrent-rw", messages, SessionData.Status.ACTIVE);
        storage.saveSession(session);
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger writeSuccess = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<SessionData> loaded = storage.loadSession("concurrent-rw");
                    if (loaded.isPresent()) {
                        readSuccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            executor.submit(() -> {
                try {
                    List<Message> newMessages = Arrays.asList(Message.user("Update"));
                    SessionData newSession = SessionData.create("concurrent-rw-" + System.nanoTime(), 
                        newMessages, SessionData.Status.ACTIVE);
                    if (storage.saveSession(newSession) != null) {
                        writeSuccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(threadCount, readSuccess.get());
        assertEquals(threadCount, writeSuccess.get());
    }

    @Test
    void testLoadCorruptedFile() throws Exception {
        Path sessionFile = tempDir.resolve("session_corrupted.json");
        Files.writeString(sessionFile, "{ invalid json content }");
        
        Optional<SessionData> loaded = storage.loadSession("corrupted");
        assertFalse(loaded.isPresent());
    }

    @Test
    void testLoadEmptyFile() throws Exception {
        Path sessionFile = tempDir.resolve("session_empty.json");
        Files.writeString(sessionFile, "");
        
        Optional<SessionData> loaded = storage.loadSession("empty");
        assertFalse(loaded.isPresent());
    }

    @Test
    void testLoadPartialJsonFile() throws Exception {
        Path sessionFile = tempDir.resolve("session_partial.json");
        Files.writeString(sessionFile, "{\"sessionId\": \"partial\"");
        
        Optional<SessionData> loaded = storage.loadSession("partial");
        assertFalse(loaded.isPresent());
    }

    @Test
    void testSessionIdUniqueness() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData session1 = SessionData.create(null, messages, SessionData.Status.ACTIVE);
        SessionData session2 = SessionData.create(null, messages, SessionData.Status.ACTIVE);
        
        SessionData saved1 = storage.saveSession(session1);
        SessionData saved2 = storage.saveSession(session2);
        
        assertNotNull(saved1);
        assertNotNull(saved2);
        assertNotNull(saved1.getSessionId());
        assertNotNull(saved2.getSessionId());
        assertNotEquals(saved1.getSessionId(), saved2.getSessionId());
    }

    @Test
    void testSessionIdConflictResolution() throws Exception {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData session1 = SessionData.create("same-id", messages, SessionData.Status.ACTIVE);
        storage.saveSession(session1);
        
        SessionData session2 = SessionData.create("same-id", messages, SessionData.Status.INTERRUPTED);
        storage.saveSession(session2);
        
        Optional<SessionData> loaded = storage.loadSession("same-id");
        assertTrue(loaded.isPresent());
        assertEquals(SessionData.Status.INTERRUPTED, loaded.get().getStatus());
    }

    @Test
    void testAtomicWriteInterruption() throws Exception {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        SessionData session = SessionData.create("atomic-test", messages, SessionData.Status.ACTIVE);
        
        storage.saveSession(session);
        
        Optional<SessionData> loaded = storage.loadSession("atomic-test");
        assertTrue(loaded.isPresent());
        
        Path tempFile = tempDir.resolve("session_atomic-test.json.tmp");
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void testLoadSessionWithNullId() {
        Optional<SessionData> loaded = storage.loadSession(null);
        assertFalse(loaded.isPresent());
    }

    @Test
    void testLoadSessionWithEmptyId() {
        Optional<SessionData> loaded = storage.loadSession("");
        assertFalse(loaded.isPresent());
    }

    @Test
    void testDeleteSessionWithNullId() {
        boolean result = storage.deleteSession(null);
        assertFalse(result);
    }

    @Test
    void testDeleteSessionWithEmptyId() {
        boolean result = storage.deleteSession("");
        assertFalse(result);
    }

    @Test
    void testDeleteNonExistentSession() {
        boolean result = storage.deleteSession("non-existent-session");
        assertFalse(result);
    }

    @Test
    void testListSessionsWithEmptyDirectory() {
        SessionStorage emptyStorage = new SessionStorage(tempDir.resolve("empty-dir"), 10);
        List<SessionData> sessions = emptyStorage.listSessions();
        assertTrue(sessions.isEmpty());
    }

    @Test
    void testCleanupCompletedSessions() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData active = SessionData.create("active-cleanup", messages, SessionData.Status.ACTIVE);
        SessionData completed = SessionData.create("completed-cleanup", messages, SessionData.Status.COMPLETED);
        
        storage.saveSession(active);
        storage.saveSession(completed);
        
        assertEquals(2, storage.listSessions().size());
        
        storage.cleanupCompletedSessions();
        
        List<SessionData> remaining = storage.listSessions();
        assertEquals(1, remaining.size());
        assertEquals("active-cleanup", remaining.get(0).getSessionId());
    }

    @Test
    void testMarkAsIgnored() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData session = SessionData.create("to-ignore", messages, SessionData.Status.INTERRUPTED);
        storage.saveSession(session);
        
        assertTrue(storage.findLatestResumableSession().isPresent());
        
        boolean result = storage.markAsIgnored("to-ignore");
        assertTrue(result);
        
        Optional<SessionData> loaded = storage.loadSession("to-ignore");
        assertTrue(loaded.isPresent());
        assertEquals(SessionData.Status.IGNORED, loaded.get().getStatus());
        
        assertFalse(storage.findLatestResumableSession().isPresent());
    }

    @Test
    void testMarkAsIgnoredNonExistentSession() {
        boolean result = storage.markAsIgnored("non-existent");
        assertFalse(result);
    }

    @Test
    void testMarkAsIgnoredWithNullId() {
        boolean result = storage.markAsIgnored(null);
        assertFalse(result);
    }

    @Test
    void testMarkAsIgnoredWithEmptyId() {
        boolean result = storage.markAsIgnored("");
        assertFalse(result);
    }

    @Test
    void testCleanupExpiredSessions() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData oldSession = SessionData.create("old-expired", messages, SessionData.Status.INTERRUPTED);
        oldSession.setLastActiveAt(LocalDateTime.now().minusHours(100));
        storage.saveSession(oldSession, false);
        
        SessionData newSession = SessionData.create("new-active", messages, SessionData.Status.INTERRUPTED);
        newSession.setLastActiveAt(LocalDateTime.now().minusHours(1));
        storage.saveSession(newSession, false);
        
        SessionData completedSession = SessionData.create("completed-old", messages, SessionData.Status.COMPLETED);
        completedSession.setLastActiveAt(LocalDateTime.now().minusHours(100));
        storage.saveSession(completedSession, false);
        
        storage.cleanupExpiredSessions(72);
        
        Optional<SessionData> oldLoaded = storage.loadSession("old-expired");
        assertTrue(oldLoaded.isPresent());
        assertEquals(SessionData.Status.IGNORED, oldLoaded.get().getStatus());
        
        Optional<SessionData> newLoaded = storage.loadSession("new-active");
        assertTrue(newLoaded.isPresent());
        assertEquals(SessionData.Status.INTERRUPTED, newLoaded.get().getStatus());
        
        Optional<SessionData> completedLoaded = storage.loadSession("completed-old");
        assertTrue(completedLoaded.isPresent());
        assertEquals(SessionData.Status.COMPLETED, completedLoaded.get().getStatus());
    }

    @Test
    void testCleanupExpiredSessionsWithZeroTimeout() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData session = SessionData.create("zero-timeout", messages, SessionData.Status.INTERRUPTED);
        session.setLastActiveAt(LocalDateTime.now().minusHours(100));
        storage.saveSession(session);
        
        storage.cleanupExpiredSessions(0);
        
        Optional<SessionData> loaded = storage.loadSession("zero-timeout");
        assertTrue(loaded.isPresent());
        assertEquals(SessionData.Status.INTERRUPTED, loaded.get().getStatus());
    }

    @Test
    void testCanResumeOnlyInterrupted() {
        List<Message> messages = Arrays.asList(Message.user("Test"));
        
        SessionData active = SessionData.create("active-resume", messages, SessionData.Status.ACTIVE);
        SessionData interrupted = SessionData.create("interrupted-resume", messages, SessionData.Status.INTERRUPTED);
        SessionData completed = SessionData.create("completed-resume", messages, SessionData.Status.COMPLETED);
        SessionData ignored = SessionData.create("ignored-resume", messages, SessionData.Status.IGNORED);
        
        assertFalse(active.canResume());
        assertTrue(interrupted.canResume());
        assertFalse(completed.canResume());
        assertFalse(ignored.canResume());
    }
}
