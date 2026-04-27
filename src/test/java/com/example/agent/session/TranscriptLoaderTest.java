package com.example.agent.session;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptLoaderTest {

    private String sessionId;
    private SessionTranscript transcript;
    private Path transcriptFile;

    @BeforeEach
    void setUp() {
        sessionId = "test-load-" + System.currentTimeMillis();
        transcript = new SessionTranscript(sessionId);
        transcriptFile = transcript.getTranscriptFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        transcript.close();
        Path file = transcript.getTranscriptFile();
        if (Files.exists(file)) {
            Files.delete(file);
            Path parent = file.getParent();
            while (parent != null && !Files.list(parent).findAny().isPresent()) {
                Files.delete(parent);
                parent = parent.getParent();
            }
        }
    }

    @Test
    void testLoadMessages() {
        transcript.appendUserMessage(Message.user("Hello"));
        transcript.appendAssistantMessage(Message.assistant("Hi"), null);
        transcript.close();

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);

        assertEquals(2, result.getMessages().size());
        assertEquals(2, result.getAllEntries().size());
        assertEquals(0, result.getTruncatedLines());
        assertFalse(result.isRecoveredFromCrash());
    }

    @Test
    void testLoadEmptyFile() {
        transcript.close();

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);

        assertEquals(0, result.getMessages().size());
        assertTrue(result.getAllEntries().isEmpty());
    }

    @Test
    void testLoadNonExistentFile() {
        TranscriptLoader.LoadResult result = TranscriptLoader.load("nonexistent-session");

        assertEquals(0, result.getMessages().size());
        assertTrue(result.getAllEntries().isEmpty());
    }

    @Test
    void testLoadMetadata() {
        transcript.appendCustomTitle("My Awesome Session");
        transcript.appendTag("bug-fix");
        transcript.appendTag("urgent");
        transcript.close();

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);

        assertEquals("My Awesome Session", result.getCustomTitle());
        assertTrue(result.getTags().contains("bug-fix"));
        assertTrue(result.getTags().contains("urgent"));
        assertEquals(2, result.getTags().size());
    }

    @Test
    void testLoadCompactBoundary() {
        transcript.appendUserMessage(Message.user("Before compact"));
        transcript.appendCompactBoundary("boundary-uuid-123");
        transcript.appendUserMessage(Message.user("After compact"));
        transcript.close();

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);

        assertEquals("boundary-uuid-123", result.getLastCompactBoundary());
        assertEquals(2, result.getMessages().size());
    }

    @Test
    void testTruncatedLineDetection() throws IOException {
        transcript.appendUserMessage(Message.user("Valid message"));
        transcript.close();

        String content = Files.readString(transcriptFile);
        Files.writeString(transcriptFile, content + "\n{\"type\":\"user\",\"uuid\":\"incomplete\",\"message\":");

        TranscriptLoader.LoadResult result = TranscriptLoader.load(transcriptFile);

        assertTrue(result.isRecoveredFromCrash());
        assertEquals(1, result.getTruncatedLines());
        assertEquals(1, result.getMessages().size());
    }

    @Test
    void testExists() {
        assertTrue(TranscriptLoader.exists(sessionId));
        assertFalse(TranscriptLoader.exists("non-existent-session"));
    }
}
