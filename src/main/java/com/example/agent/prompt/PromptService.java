package com.example.agent.prompt;

import com.example.agent.intent.IntentType;
import com.example.agent.prompt.model.Prompt;
import com.example.agent.prompt.model.TaskMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptService {

    private static final Logger logger = LoggerFactory.getLogger(PromptService.class);

    public record TaskContext(
            TaskMode mode,
            IntentType intentType,
            String userInput,
            boolean needsToolEnhancement,
            int maxPromptTokens
    ) {
        public static TaskContext defaultContext() {
            return new TaskContext(TaskMode.CODING, null, null, false, 8000);
        }

        public static TaskContext forMode(TaskMode mode) {
            return new TaskContext(mode, null, null, false, 8000);
        }

        public static TaskContext withIntent(IntentType intentType) {
            return new TaskContext(TaskMode.CODING, intentType, null, false, 8000);
        }
    }

    private final PromptLibrary library;
    private boolean enabled = true;
    private boolean autoInjectExpert = true;

    public PromptService() {
        this.library = PromptLibrary.getInstance();
        this.library.initialize();
    }

    public String getSystemPrompt(TaskContext context) {
        if (!enabled) {
            logger.debug("Prompt library disabled, using fallback");
            return library.getFallbackPrompt();
        }
        return library.buildSystemPrompt(context);
    }

    public String getBasePrompt(TaskMode mode) {
        Prompt prompt = library.getBasePrompt(mode);
        return prompt != null ? prompt.getContent() : library.getFallbackPrompt();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAutoInjectExpert(boolean autoInjectExpert) {
        this.autoInjectExpert = autoInjectExpert;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutoInjectExpert() {
        return autoInjectExpert;
    }
}
