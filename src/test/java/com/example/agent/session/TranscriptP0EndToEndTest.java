package com.example.agent.session;

import com.example.agent.llm.model.Message;
import com.example.agent.service.ConversationManager;
import com.example.agent.service.TokenEstimator;
import com.example.agent.logging.WorkspaceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptP0EndToEndTest {

    private String sessionId;
    private ConversationManager manager;
    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        sessionId = "p0-test-" + System.currentTimeMillis();
        tokenEstimator = new TokenEstimator();
        manager = new ConversationManager("You are a helper", tokenEstimator);
    }

    @AfterEach
    void tearDown() {
        manager.disableTranscript();
    }

    @Test
    void testTranscriptPersistenceAndRecovery() {
        manager.enableTranscript(sessionId);

        manager.addUserMessage("Hello, how are you?");
        manager.addAssistantMessage(Message.assistant("I'm fine, thank you!"));
        manager.addUserMessage("What's Java?");
        manager.addAssistantMessage(Message.assistant("Java is a programming language"));

        manager.disableTranscript();

        ConversationManager newManager = new ConversationManager("You are a helper", tokenEstimator);
        
        boolean loaded = TranscriptLoader.loadToConversationManager(sessionId, newManager);
        assertTrue(loaded);
        
        assertEquals(5, newManager.getMessageCount());
        assertEquals("Hello, how are you?", newManager.getHistory().get(1).getContent());
        assertEquals("Java is a programming language", newManager.getHistory().get(4).getContent());
    }

    @Test
    void testSessionStoragePrefersTranscript() {
        manager.enableTranscript(sessionId);
        manager.addUserMessage("Transcript message");
        manager.disableTranscript();

        SessionStorage storage = new SessionStorage();
        var loaded = storage.loadSession(sessionId);

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().getMessages().stream()
            .anyMatch(m -> "Transcript message".equals(m.getContent())));
    }

    @Test
    void testCrashRecoveryWithTruncatedLine() throws IOException {
        manager.enableTranscript(sessionId);
        manager.addUserMessage("Message before crash");
        manager.disableTranscript();

        Path transcriptFile = WorkspaceManager.getSessionMessagesFile(
            WorkspaceManager.getCurrentProjectKey(), sessionId
        );

        Files.writeString(transcriptFile, "{\"type\":\"user\",\"uuid\":\"broken\",\"message\":\n", 
            java.nio.file.StandardOpenOption.APPEND);

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);

        assertTrue(result.isRecoveredFromCrash());
        assertEquals(1, result.getTruncatedLines());
        assertEquals(2, result.getMessages().size());
        assertTrue(result.getMessages().stream()
            .anyMatch(m -> "Message before crash".equals(m.getContent())));
    }

    @Test
    void testRepairAndCompact() throws IOException {
        manager.enableTranscript(sessionId);
        manager.addUserMessage("Good message 1");
        manager.addUserMessage("Good message 2");
        manager.disableTranscript();

        Path transcriptFile = WorkspaceManager.getSessionMessagesFile(
            WorkspaceManager.getCurrentProjectKey(), sessionId
        );

        Files.writeString(transcriptFile, "{incomplete json\n", 
            java.nio.file.StandardOpenOption.APPEND);

        int repaired = TranscriptLoader.repairAndCompact(transcriptFile);
        assertEquals(1, repaired);

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);
        assertFalse(result.isRecoveredFromCrash());
        assertEquals(0, result.getTruncatedLines());
        assertEquals(3, result.getMessages().size());
    }

    @Test
    void testResumeSessionUsesTranscript() {
        manager.enableTranscript(sessionId);
        manager.addUserMessage("This is from transcript");
        manager.addAssistantMessage(Message.assistant("Got it!"));
        manager.disableTranscript();

        SessionData sessionData = SessionData.create(sessionId, 
            java.util.List.of(Message.user("This is from old snapshot")),
            SessionData.Status.INTERRUPTED);

        ConversationManager resumeManager = new ConversationManager("You are helper", tokenEstimator);
        
        boolean loadedFromTranscript = TranscriptLoader.loadToConversationManager(
            sessionId, resumeManager
        );

        assertTrue(loadedFromTranscript);
        assertTrue(resumeManager.getHistory().stream()
            .anyMatch(m -> "This is from transcript".equals(m.getContent())));
    }

    @Test
    void testListSessionsWithoutLoadingFullFiles() {
        manager.enableTranscript(sessionId);
        manager.addUserMessage("List test message");
        manager.disableTranscript();

        var sessions = TranscriptLister.listSessions();

        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream()
            .anyMatch(s -> sessionId.equals(s.getSessionId())));
    }

    @Test
    void testTranscriptIsAutoEnabledOnNewSession() {
        manager.enableTranscript(sessionId);
        assertNotNull(manager.getTranscript());
        assertTrue(manager.getTranscript().isAvailable());
    }
}
