package com.example.agent.session;

import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.logging.WorkspaceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionTranscriptTest {

    private String sessionId;
    private SessionTranscript transcript;

    @BeforeEach
    void setUp() {
        sessionId = "test-session-" + System.currentTimeMillis();
        transcript = new SessionTranscript(sessionId);
    }

    @AfterEach
    void tearDown() {
        transcript.close();
    }

    @Test
    void testAppendUserMessage() throws IOException {
        Message message = Message.user("Hello, World!");
        
        transcript.appendUserMessage(message);
        transcript.close();

        Path file = transcript.getTranscriptFile();
        assertTrue(Files.exists(file));
        
        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"type\":\"user\""));
        assertTrue(lines.get(0).contains(message.getId()));
    }

    @Test
    void testAppendAssistantMessageWithUsage() throws IOException {
        Message message = Message.assistant("Hi there!");
        Usage usage = new Usage(100, 50, 150);
        
        transcript.appendAssistantMessage(message, usage);
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"type\":\"assistant\""));
        assertTrue(lines.get(0).contains("\"totalTokens\":150"));
    }

    @Test
    void testAppendToolResult() throws IOException {
        Message message = Message.toolResult("call-123", "grep", "result content");
        
        transcript.appendToolResult(message, "grep", 150, true);
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"type\":\"tool-result\""));
        assertTrue(lines.get(0).contains("\"toolName\":\"grep\""));
        assertTrue(lines.get(0).contains("\"toolSuccess\":true"));
        assertTrue(lines.get(0).contains("\"toolDurationMs\":150"));
    }

    @Test
    void testAppendMultipleMessages() throws IOException {
        transcript.appendUserMessage(Message.user("Message 1"));
        transcript.appendAssistantMessage(Message.assistant("Response 1"), null);
        transcript.appendUserMessage(Message.user("Message 2"));
        transcript.appendSystemMessage("System notification");
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(4, lines.size());
    }

    @Test
    void testIsAvailable() {
        assertTrue(transcript.isAvailable());
    }

    @Test
    void testAppendCustomEntry() throws IOException {
        TranscriptEntry entry = TranscriptEntry.customTitle(sessionId, "My Custom Title");
        
        transcript.append(entry);
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"type\":\"custom-title\""));
        assertTrue(lines.get(0).contains("\"title\":\"My Custom Title\""));
    }

    @Test
    void testUuidDeduplication() throws IOException {
        Message message = Message.user("Hello, World!");
        String messageUuid = message.getId();
        assertNotNull(messageUuid);

        transcript.appendUserMessage(message);
        transcript.appendUserMessage(message);
        transcript.appendUserMessage(message);
        transcript.forceFlush();

        assertEquals(1, transcript.getUuidCacheSize());
        assertTrue(transcript.hasUuid(messageUuid));

        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(1, lines.size(), "重复UUID的消息应该只写入一次");
    }

    @Test
    void testLoadExistingUuidsOnStartup() throws IOException {
        Message message = Message.user("First message");
        transcript.appendUserMessage(message);
        transcript.close();

        String existingUuid = message.getId();

        SessionTranscript newTranscript = new SessionTranscript(sessionId);
        try {
            assertTrue(newTranscript.isAvailable());
            assertEquals(1, newTranscript.getUuidCacheSize(), "重启后应该从磁盘加载已有UUID");
            assertTrue(newTranscript.hasUuid(existingUuid));

            newTranscript.appendUserMessage(message);
            newTranscript.forceFlush();

            List<String> lines = Files.readAllLines(newTranscript.getTranscriptFile());
            assertEquals(1, lines.size(), "重启后重复消息也应该去重");
        } finally {
            newTranscript.close();
        }
    }

    @Test
    void testToolResultDeduplication() throws IOException, InterruptedException {
        Message toolMessage = Message.toolResult("call-123", "grep", "result");
        String toolUuid = toolMessage.getId();

        transcript.appendToolResult(toolMessage, "grep", 100, true);
        transcript.appendToolResult(toolMessage, "grep", 100, true);
        transcript.forceFlush();

        assertTrue(transcript.hasUuid(toolUuid));
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(1, lines.size(), "工具结果消息也应该去重");
    }

    @Test
    void testDifferentUuidsNotDeduplicated() throws IOException {
        Message msg1 = Message.user("Message 1");
        Message msg2 = Message.user("Message 2");

        assertNotEquals(msg1.getId(), msg2.getId());

        transcript.appendUserMessage(msg1);
        transcript.appendUserMessage(msg2);
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(2, lines.size(), "不同UUID的消息不应该被去重");
    }

    @Test
    void testCompactionScenarioSimulation() throws IOException {
        Message originalMsg = Message.user("Original message");
        transcript.appendUserMessage(originalMsg);
        transcript.forceFlush();

        Message compactedMsg = new Message();
        compactedMsg.setId(originalMsg.getId());
        compactedMsg.setRole("user");
        compactedMsg.setContent("Compacted version of the message");

        transcript.appendUserMessage(compactedMsg);
        transcript.close();

        List<String> lines = Files.readAllLines(transcript.getTranscriptFile());
        assertEquals(1, lines.size(), "压缩时重用UUID的消息应该被去重");
    }
}
