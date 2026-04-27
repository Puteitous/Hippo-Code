package com.example.agent.core;

import com.example.agent.application.ConversationService;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.logging.WorkspaceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionIdConsistencyTest {

    private final List<String> testSessionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testSessionIds.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        String projectKey = WorkspaceManager.getCurrentProjectKey();
        for (String sessionId : testSessionIds) {
            Path sessionDir = WorkspaceManager.getSessionDir(projectKey, sessionId);
            deleteRecursively(sessionDir);
        }
        testSessionIds.clear();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.list(path).forEach(child -> {
                try {
                    deleteRecursively(child);
                } catch (IOException e) {
                }
            });
        }
        Files.deleteIfExists(path);
    }

    @Test
    void testAgentContextAndConversationHaveSameSessionId() throws Exception {
        AgentContext context = new AgentContext();
        context.initialize();
        testSessionIds.add(context.getSessionId());

        String agentContextId = context.getSessionId();
        String conversationId = context.getConversation().getSessionId();

        assertEquals(agentContextId, conversationId,
            "AgentContext and Conversation MUST share the same sessionId!\n" +
            "AgentContext.sessionId = " + agentContextId + "\n" +
            "Conversation.sessionId = " + conversationId + "\n" +
            "This bug causes Sub-Agent Fork mode to fail silently!");
    }

    @Test
    void testConversationRegisteredInServiceWithCorrectId() throws Exception {
        AgentContext context = new AgentContext();
        context.initialize();
        testSessionIds.add(context.getSessionId());

        ConversationService service = ServiceLocator.get(ConversationService.class);
        String agentContextId = context.getSessionId();

        assertNotNull(service.getConversation(agentContextId),
            "Conversation should be registered with AgentContext.sessionId = " + agentContextId);
        assertEquals(agentContextId, service.getConversation(agentContextId).getSessionId());
    }

    @Test
    void testResetConversationMaintainsSessionId() throws Exception {
        AgentContext context = new AgentContext();
        context.initialize();
        testSessionIds.add(context.getSessionId());

        String originalSessionId = context.getSessionId();

        context.resetConversation();

        assertEquals(originalSessionId, context.getSessionId(),
            "resetConversation() should NOT change AgentContext.sessionId");
        assertEquals(originalSessionId, context.getConversation().getSessionId(),
            "resetConversation() should preserve sessionId for new Conversation");
    }
}
