package com.example.agent.prompt;

import com.example.agent.intent.IntentType;
import com.example.agent.prompt.loader.ClasspathPromptLoader;
import com.example.agent.prompt.loader.PromptLoader;
import com.example.agent.prompt.model.Prompt;
import com.example.agent.prompt.model.PromptType;
import com.example.agent.prompt.model.TaskMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PromptLibrary {

    private static final Logger logger = LoggerFactory.getLogger(PromptLibrary.class);

    private static PromptLibrary instance;

    private final Map<PromptType, Prompt> promptCache;
    private final PromptLoader loader;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private PromptLibrary() {
        this.promptCache = new ConcurrentHashMap<>();
        this.loader = new ClasspathPromptLoader();
    }

    public static PromptLibrary getInstance() {
        if (instance == null) {
            synchronized (PromptLibrary.class) {
                if (instance == null) {
                    instance = new PromptLibrary();
                }
            }
        }
        return instance;
    }

    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.info("Initializing Prompt Library...");
            List<Prompt> prompts = loader.loadAll();
            for (Prompt prompt : prompts) {
                if (prompt.isEnabled()) {
                    promptCache.put(prompt.getType(), prompt);
                }
            }
            logger.info("Prompt Library initialized: {} prompts loaded", promptCache.size());
        }
    }

    public String buildSystemPrompt(PromptService.TaskContext context) {
        StringBuilder sb = new StringBuilder(4096);

        Prompt basePrompt = getBasePrompt(context.mode());
        if (basePrompt != null) {
            sb.append(basePrompt.getContent());
        } else {
            sb.append(getFallbackPrompt());
        }

        if (context.intentType() != null) {
            getTaskPrompt(context.intentType()).ifPresent(taskPrompt -> {
                sb.append("\n\n");
                sb.append("===== 任务模式: ").append(taskPrompt.getType().getDisplayName()).append(" =====\n");
                sb.append(taskPrompt.getContent());
            });
        }

        return sb.toString();
    }

    public Prompt getBasePrompt(TaskMode mode) {
        PromptType type = mode != null ? mode.getDefaultBasePromptType() : PromptType.BASE_CODING;
        return promptCache.get(type);
    }

    public Optional<Prompt> getTaskPrompt(IntentType intentType) {
        if (intentType == null) {
            return Optional.empty();
        }

        return switch (intentType) {
            case CODE_MODIFICATION -> Optional.ofNullable(promptCache.get(PromptType.TASK_REFACTOR));
            case DEBUGGING -> Optional.ofNullable(promptCache.get(PromptType.TASK_DEBUG));
            case CODE_GENERATION -> Optional.ofNullable(promptCache.get(PromptType.TASK_CODEGEN));
            case CODE_REVIEW -> Optional.ofNullable(promptCache.get(PromptType.TASK_REVIEW));
            case PROJECT_ANALYSIS -> Optional.ofNullable(promptCache.get(PromptType.TASK_ARCHITECTURE));
            default -> Optional.empty();
        };
    }

    public Optional<Prompt> getPrompt(PromptType type) {
        return Optional.ofNullable(promptCache.get(type));
    }

    public String getFallbackPrompt() {
        throw new IllegalStateException(
            "Prompt files not found! Please check resources/prompts/ directory exists " +
            "and contains base_chat.md / base_coding.md"
        );
    }

    public void reload() {
        initialized.set(false);
        promptCache.clear();
        initialize();
    }

    public Collection<Prompt> getAllPrompts() {
        return promptCache.values();
    }
}
