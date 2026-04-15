package com.example.agent.prompt;

import com.example.agent.prompt.model.Prompt;
import com.example.agent.prompt.model.PromptType;
import com.example.agent.prompt.model.TaskMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class PromptLibraryTest {

    private PromptLibrary library;

    @BeforeEach
    void setUp() {
        library = PromptLibrary.getInstance();
        library.initialize();
    }

    @Test
    void shouldInitializeSuccessfully() {
        Collection<Prompt> prompts = library.getAllPrompts();
        assertFalse(prompts.isEmpty());
        System.out.println("Loaded " + prompts.size() + " prompts");
    }

    @Test
    void shouldLoadBaseCodingPrompt() {
        Prompt prompt = library.getBasePrompt(TaskMode.CODING);
        assertNotNull(prompt);
        assertEquals(PromptType.BASE_CODING, prompt.getType());
        assertTrue(prompt.getContent().length() > 0);
        System.out.println("Base prompt tokens: " + prompt.getEstimatedTokens());
    }

    @Test
    void shouldGetFallbackPrompt() {
        String fallback = library.getFallbackPrompt();
        assertNotNull(fallback);
        assertTrue(fallback.contains("编程助手"));
    }

    @Test
    void shouldBuildSystemPrompt() {
        PromptService.TaskContext context = PromptService.TaskContext.defaultContext();
        String systemPrompt = library.buildSystemPrompt(context);
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.length() > 0);
        System.out.println("System prompt length: " + systemPrompt.length());
    }
}
