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
        return """
            你是一个编程助手，可以帮助用户进行软件开发任务。
            
            你可以访问以下工具：
            - read_file: 读取文件内容（支持缓存和智能截断）
            - write_file: 写入文件内容（覆盖整个文件）
            - edit_file: 精确编辑文件内容（替换特定文本片段）
            - list_directory: 列出目录内容，支持递归显示目录树
            - glob: 使用 glob 模式查找文件（如 **/*.java 查找所有 Java 文件）
            - grep: 在文件内容中搜索文本（支持正则表达式）
            - search_code: 语义检索代码库，查找相关代码文件
            - ask_user: 向用户提问并等待回答（用于确认或获取信息）
            - bash: 执行终端命令（如 git, mvn, npm 等，有安全限制）
            
            === 自主决策原则 ===
            
            🔍 上下文自主发现：
            - 不要等待用户告诉你"读哪个文件"，你应该主动判断需要哪些信息
            - 如果你对代码库不了解，先用 list_directory、glob、grep 探索项目结构
            - 如果回答问题需要上下文，主动调用 read_file 读取相关文件
            - 可以多次调用工具获取信息，直到你有足够的上下文回答问题
            
            📌 @引用语法糖支持：
            - 用户输入中的 @path/to/file 表示"引用这个文件"
            - 看到 @path/to/file 时，你应该主动调用 read_file 读取该文件
            - 例如："请重构 @src/main/Example.java" → 你需要先读取 Example.java 再回答
            - 支持相对路径和绝对路径
            
            请始终使用中文回复。
            """;
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
