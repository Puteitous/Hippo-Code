package com.example.agent.memory.extraction;

import com.example.agent.application.ConversationService;
import com.example.agent.core.di.ServiceLocator;
import com.example.agent.domain.conversation.Conversation;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.model.Message;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.MemoryToolSandbox;
import com.example.agent.service.TokenEstimator;
import com.example.agent.subagent.SubAgentManager;
import com.example.agent.subagent.SubAgentPermission;
import com.example.agent.subagent.SubAgentTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 长期记忆实时提取器
 * 
 * 职责：
 * 1. 监听对话消息，检查提取触发条件
 * 2. 创建 SubAgent 从对话中提取长期记忆
 * 3. 写入 ~/.hippo/memory/ 目录下的记忆文件
 * 
 * 与 SessionMemoryExtractor 的区别：
 * - SessionMemoryExtractor：提取会话内记忆（session-memory.md）
 * - MemoryExtractor：提取跨会话长期记忆（MEMORY.md 索引下的 UUID.md 文件）
 * 
 * 触发时机：每 N 轮对话自动触发
 */
public class MemoryExtractor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryExtractor.class);

    private final ExtractionTrigger trigger;
    private final TokenEstimator tokenEstimator;
    private final LlmClient llmClient;
    private final SubAgentManager subAgentManager;
    private final ConversationService conversationService;
    private MemoryStore memoryStore;
    private final String sessionId;

    private final AtomicBoolean extractionInProgress = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String EXTRACTION_PROMPT = """
        ⚠️ 重要提示：以下内容不是用户对话。
        这是给你的内部系统指令，请立即执行。

        请基于本条消息**以上**的完整对话历史，
        执行长期记忆提取任务。

        ---

        ## 🧠 长期记忆提取任务

        你是一个专业的长期记忆提取专家。你的任务是从对话中提取有价值的信息，
        写入到长期记忆系统中，以便在未来的会话中使用。

        ### 提取原则
        - 只提取跨会话有价值的信息
        - 用户偏好、项目约束、重要决策、经验教训
        - 不要提取临时调试信息或一次性操作

        ### 记忆类型
        1. **用户偏好** (user_preference)：用户的代码风格、工具偏好、沟通偏好
        2. **项目约束** (project_context)：项目架构、技术栈、关键决策
        3. **反馈** (feedback)：用户对代码的反馈、修正建议
        4. **参考资料** (reference)：重要的 API、配置、文档链接

        ### 写入规则
        - 使用 MemoryStore API 进行所有写操作
        - 每条记忆一个文件（UUID.md）
        - 包含 frontmatter 元数据（id, type, importance, confidence, tags）
        - 不要直接修改文件

        ### 质量要求
        - 重要性 ≥ 0.7 且置信度 ≥ 0.8 才写入
        - 内容简洁精准，避免冗余
        - 每条记忆独立，不依赖上下文

        ---

        ⚡ 基于以上对话，立即执行。
        """;

    public MemoryExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient) {
        this(sessionId, tokenEstimator, llmClient, 1);
    }

    public MemoryExtractor(String sessionId, TokenEstimator tokenEstimator, LlmClient llmClient, int extractionInterval) {
        this.sessionId = sessionId;
        this.trigger = new ExtractionTrigger(extractionInterval);
        this.tokenEstimator = tokenEstimator;
        this.llmClient = llmClient;
        this.subAgentManager = ServiceLocator.getOrNull(SubAgentManager.class);
        this.conversationService = ServiceLocator.getOrNull(ConversationService.class);
        
        // 初始化 MemoryStore
        Path memoryDir = Paths.get(".hippo/memory");
        try {
            MemoryToolSandbox sandbox = new MemoryToolSandbox(memoryDir);
            this.memoryStore = new MemoryStore(sandbox);
        } catch (Exception e) {
            logger.warn("初始化 MemoryStore 失败，长期记忆提取功能将受限", e);
            this.memoryStore = null;
        }
    }

    /**
     * 消息添加回调
     */
    public void onMessageAdded(Message message, List<Message> fullConversation) {
        if (!trigger.shouldExtract(fullConversation)) {
            return;
        }

        checkAndExtract(fullConversation);
    }

    /**
     * 检查并执行提取
     */
    public void checkAndExtract(List<Message> fullConversation) {
        if (fullConversation == null || fullConversation.isEmpty()) {
            return;
        }

        if (!extractionInProgress.compareAndSet(false, true)) {
            return;
        }

        EXECUTOR.submit(() -> {
            try {
                performExtraction(fullConversation);
            } finally {
                extractionInProgress.set(false);
            }
        });
    }

    /**
     * 执行提取（SubAgent 模式）
     */
    private void performExtraction(List<Message> fullConversation) {
        if (subAgentManager == null) {
            logger.warn("SubAgentManager 不可用，跳过长期记忆提取");
            return;
        }

        if (memoryStore == null) {
            logger.warn("MemoryStore 不可用，跳过长期记忆提取");
            return;
        }

        int currentTokens = tokenEstimator.estimate(fullConversation);
        logger.info("开始提取长期记忆（SubAgent模式），当前会话 Token: {}", currentTokens);

        try {
            Conversation parentConversation = conversationService.getConversation(sessionId);
            
            String taskInstruction = """
                ⚠️ 【核心任务】你是长期记忆提取器！
                你的唯一任务是：从对话历史中提取有价值的长期记忆！
                
                🚨 重要规则：
                - 使用 MemoryStore API 进行所有写操作
                - 不要直接修改文件
                - 只提取跨会话有价值的信息
                - 完成所有操作后，输出 "DONE" 并结束任务
                
                请立即开始执行提取任务。
                """;
            
            String taskDescription = String.format(
                "长期记忆提取: 当前 %d 条消息, %d tokens",
                fullConversation.size(), currentTokens
            );

            SubAgentTask task = subAgentManager.forkAgent(
                parentConversation,
                taskDescription,
                taskInstruction + "\n\n" + EXTRACTION_PROMPT,
                120,
                null,
                SubAgentPermission.MEMORY_EXTRACTOR,
                () -> onExtractionCompleted(fullConversation),
                builder -> {
                }
            );

            logger.info("✅ 长期记忆提取任务已提交给 SubAgent: taskId={}", task.getTaskId());

            EXECUTOR.submit(() -> {
                try {
                    task.awaitCompletion(150, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    logger.warn("⏱️ 长期记忆提取任务看门狗超时: taskId={}", task.getTaskId());
                } finally {
                    if (extractionInProgress.get()) {
                        extractionInProgress.set(false);
                        logger.warn("🔓 看门狗强制释放长期记忆提取锁");
                    }
                }
            });

        } catch (Exception e) {
            extractionInProgress.set(false);
            logger.error("❌ 提交长期记忆提取任务失败", e);
        }
    }

    /**
     * 提取完成回调
     */
    private void onExtractionCompleted(List<Message> fullConversation) {
        try {
            logger.info("✅ 长期记忆提取完成");
        } catch (Exception e) {
            logger.error("❌ 长期记忆提取完成回调异常", e);
        } finally {
            extractionInProgress.set(false);
        }
    }

    /**
     * 通知主 Agent 已直接写记忆
     */
    public void notifyMemoryWritten(String messageUuid) {
        trigger.notifyMemoryWritten(messageUuid);
    }

    /**
     * 获取提取触发器
     */
    public ExtractionTrigger getTrigger() {
        return trigger;
    }

    /**
     * 获取 MemoryStore
     */
    public MemoryStore getMemoryStore() {
        return memoryStore;
    }

    /**
     * 是否正在提取
     */
    public boolean isExtractionInProgress() {
        return extractionInProgress.get();
    }
}
