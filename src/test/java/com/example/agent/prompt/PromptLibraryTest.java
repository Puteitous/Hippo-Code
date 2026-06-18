package com.example.agent.prompt;

import com.example.agent.prompt.model.Prompt;
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
        System.out.println("Loaded " + prompts.size() + " prompts:");
        prompts.forEach(p -> System.out.println("  - " + p.getType() + " (priority=" + p.getPriority() + ", enabled=" + p.isEnabled() + ")"));
    }

    @Test
    void shouldBuildCodingModePrompt() {
        String prompt = library.getBasePrompt(TaskMode.CODING);
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        assertTrue(prompt.contains("编程助手"));
        assertTrue(prompt.contains("构建模式"));
        System.out.println("Coding mode prompt length: " + prompt.length());
    }

    @Test
    void shouldBuildChatModePrompt() {
        String prompt = library.getBasePrompt(TaskMode.CHAT);
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        assertTrue(prompt.contains("顾问模式"));
        System.out.println("Chat mode prompt length: " + prompt.length());
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
