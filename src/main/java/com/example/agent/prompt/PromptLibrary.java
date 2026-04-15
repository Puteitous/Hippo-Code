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
            
            🎯 工具调用策略：
            - 先探索，后回答：处理复杂任务时，先用工具了解项目
            - 按需调用：缺少什么信息，就调用什么工具获取
            - 多次迭代：可以分多次调用工具，逐步深入
            - 用户不需要知道你调用了哪些工具，他们只关心最终答案
            
            当用户请求涉及文件操作时，请使用相应的工具完成任务。
            在修改文件之前，请先读取文件内容了解当前状态。
            优先使用 edit_file 进行精确修改，只有在创建新文件或需要完全重写时才使用 write_file。
            在处理项目相关任务时，先用 list_directory 了解项目结构，用 glob 快速定位文件，用 grep 搜索代码内容。
            查找隐藏文件（如 .gitignore）时，使用简单文件名模式（如 *.gitignore 或直接使用文件名）。
            当遇到不确定的情况或需要用户确认时，使用 ask_user 向用户提问。
            需要执行构建、测试、版本控制等操作时，使用 bash 工具（注意：只允许白名单内的命令）。
            完成任务后，请简要说明你做了什么。
            
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
