package com.example.agent.plan;

import com.example.agent.intent.IntentResult;
import com.example.agent.intent.IntentType;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompositeTaskPlannerTest {

    private CompositeTaskPlanner planner;
    private SimpleTaskPlanner simplePlanner;
    private LlmTaskPlanner llmPlanner;
    private LlmClient mockLlmClient;
    private PlanningContext context;

    @BeforeEach
    void setUp() throws LlmException {
        mockLlmClient = mock(LlmClient.class);
        
        ChatResponse mockResponse = mock(ChatResponse.class);
        when(mockResponse.getContent()).thenReturn("""
            {
                "strategy": "SEQUENTIAL",
                "steps": [
                    {"id": "step-1", "type": "LLM_CALL", "description": "分析请求"}
                ]
            }
            """);
        when(mockLlmClient.chat(any())).thenReturn(mockResponse);

        simplePlanner = new SimpleTaskPlanner();
        llmPlanner = new LlmTaskPlanner(mockLlmClient);
        planner = new CompositeTaskPlanner(simplePlanner, llmPlanner);

        context = PlanningContext.builder()
                .userInput("测试输入")
                .currentRound(1)
                .build();
    }

    private IntentResult createIntent(IntentType type, double confidence) {
        return IntentResult.builder()
                .type(type)
                .confidence(confidence)
                .build();
    }

    @Test
    void testUsesSimplePlannerForHighConfidenceSimpleIntent() {
        IntentResult intent = createIntent(IntentType.QUESTION, 0.95);
        planner.setPreferLlm(false);

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        assertEquals(intent, plan.getIntent());
    }

    @Test
    void testUsesLlmPlannerForLowConfidence() throws LlmException {
        IntentResult intent = createIntent(IntentType.QUESTION, 0.3);
        planner.setPreferLlm(false);

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        verify(mockLlmClient, atLeastOnce()).chat(any());
    }

    @Test
    void testUsesLlmPlannerForDebugging() throws LlmException {
        IntentResult intent = createIntent(IntentType.DEBUGGING, 0.9);
        planner.setPreferLlm(false);

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        verify(mockLlmClient, atLeastOnce()).chat(any());
    }

    @Test
    void testUsesLlmPlannerForProjectAnalysis() throws LlmException {
        IntentResult intent = createIntent(IntentType.PROJECT_ANALYSIS, 0.9);
        planner.setPreferLlm(false);

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        verify(mockLlmClient, atLeastOnce()).chat(any());
    }

    @Test
    void testPreferLlmForcesLlmPlanner() throws LlmException {
        IntentResult intent = createIntent(IntentType.QUESTION, 0.95);
        planner.setPreferLlm(true);

        ExecutionPlan plan = planner.plan(intent, context);

        assertNotNull(plan);
        verify(mockLlmClient, atLeastOnce()).chat(any());
    }

    @Test
    void testSupportsAllIntentTypes() {
        for (IntentType type : IntentType.values()) {
            assertTrue(planner.supports(type), "应该支持意图类型: " + type);
        }
    }

    @Test
    void testIsEnabledByDefault() {
        assertTrue(planner.isEnabled());
    }

    @Test
    void testGetPriority() {
        assertTrue(planner.getPriority() >= 0);
    }

    @Test
    void testGetName() {
        assertEquals("CompositeTaskPlanner", planner.getName());
    }

    @Test
    void testGetSimplePlanner() {
        assertNotNull(planner.getSimplePlanner());
        assertSame(simplePlanner, planner.getSimplePlanner());
    }

    @Test
    void testGetLlmPlanner() {
        assertNotNull(planner.getLlmPlanner());
        assertSame(llmPlanner, planner.getLlmPlanner());
    }

    @Test
    void testSetPreferLlm() {
        assertFalse(planner.isPreferLlm());
        
        planner.setPreferLlm(true);
        assertTrue(planner.isPreferLlm());
        
        planner.setPreferLlm(false);
        assertFalse(planner.isPreferLlm());
    }

    @Test
    void testHandlesNullIntent() {
        ExecutionPlan plan = planner.plan(null, context);
        assertNotNull(plan);
    }

    @Test
    void testHandlesComplexCodeModification() {
        IntentResult intent = IntentResult.builder()
                .type(IntentType.CODE_MODIFICATION)
                .confidence(0.9)
                .entity("file", "test.java")
                .entity("line", 10)
                .entity("content", "new code")
                .build();

        ExecutionPlan plan = planner.plan(intent, context);
        assertNotNull(plan);
    }
}
