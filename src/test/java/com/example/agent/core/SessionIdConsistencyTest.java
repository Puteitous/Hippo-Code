package com.example.agent.core;

import com.example.agent.application.ConversationService;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.logging.WorkspaceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionIdConsistencyTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        WorkspaceManager.overrideBasePath(tempDir);
    }

    @Test
    void testAgentContextAndConversationHaveSameSessionId() throws Exception {
        AgentContext context = new AgentContext();
        context.initialize();

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

        String originalSessionId = context.getSessionId();

        context.resetConversation();

        assertEquals(originalSessionId, context.getSessionId(),
            "resetConversation() should NOT change AgentContext.sessionId");
        assertEquals(originalSessionId, context.getConversation().getSessionId(),
            "resetConversation() should preserve sessionId for new Conversation");
    }
}
