package com.example.agent.performance;

import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.example.agent.llm.model.Usage;
import com.example.agent.logging.TokenMetricsCollector;
import com.example.agent.service.TiktokenEstimator;
import com.example.agent.service.TokenEstimator;
import com.example.agent.testutil.LlmResponseBuilder;
import com.example.agent.testutil.MockLlmClient;
import com.example.agent.testutil.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.agent.testutil.TestFixtures.ToolCalls.bashToolCall;
import static com.example.agent.testutil.TestFixtures.ToolCalls.grepToolCall;
import static com.example.agent.testutil.TestFixtures.ToolCalls.readFileToolCall;
import static com.example.agent.testutil.TestFixtures.ToolCalls.writeFileToolCall;

@DisplayName("复杂任务Token消耗模拟测试")
public class TaskExecutionTokenSimulationTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutionTokenSimulationTest.class);

    private MockLlmClient mockLlmClient;
    private TokenEstimator tokenEstimator;
    private TokenMetricsCollector metricsCollector;
    private List<Message> conversationHistory;

    private final Map<String, TaskTokenStats> statsMap = new HashMap<>();

    @BeforeEach
    void setUp() {
        mockLlmClient = new MockLlmClient();
        tokenEstimator = new TiktokenEstimator();
        metricsCollector = new TokenMetricsCollector(LocalDate.now());
        conversationHistory = new ArrayList<>();
        statsMap.clear();
    }

    @AfterEach
    void tearDown() {
        printSummaryReport();
    }

    @Test
    @DisplayName("场景1: 大型代码重构任务")
    void simulateLargeCodeRefactoringTask() throws LlmException {
        String taskId = "code-refactoring-" + System.currentTimeMillis();
        TaskTokenStats stats = new TaskTokenStats("大型代码重构");

        mockLlmClient.enqueueResponses(generateRefactoringResponseSequence());

        simulateConversation(taskId, stats, List.of(
                "请帮我重构这个项目的错误处理模块，目前到处都是try-catch重复代码",
                "需要统一的异常层级结构",
                "要添加重试机制",
                "错误码需要标准化",
                "需要添加日志埋点",
                "完成后请写单元测试"
        ));

        statsMap.put(taskId, stats);
    }

    @Test
    @DisplayName("场景2: 复杂Bug排查任务")
    void simulateComplexBugDebuggingTask() throws LlmException {
        String taskId = "bug-debugging-" + System.currentTimeMillis();
        TaskTokenStats stats = new TaskTokenStats("复杂Bug排查");

        mockLlmClient.enqueueResponses(generateDebuggingResponseSequence());

        simulateConversation(taskId, stats, List.of(
                "用户报告支付超时",
                "错误日志显示NullPointerException在PaymentService第456行",
                "数据库连接池经常耗尽",
                "高并发下出现死锁",
                "帮我分析根因并提供修复方案"
        ));

        statsMap.put(taskId, stats);
    }

    @Test
    @DisplayName("场景3: 新功能完整开发")
    void simulateNewFeatureDevelopment() throws LlmException {
        String taskId = "feature-dev-" + System.currentTimeMillis();
        TaskTokenStats stats = new TaskTokenStats("新功能完整开发");

        mockLlmClient.enqueueResponses(generateFeatureDevResponseSequence());

        simulateConversation(taskId, stats, List.of(
                "我需要添加用户权限管理功能",
                "RBAC模型设计",
                "数据库表结构设计",
                "DAO层实现",
                "Service层实现",
                "Controller层实现",
                "添加Spring Security配置",
                "添加JWT认证",
                "编写接口文档",
                "编写集成测试"
        ));

        statsMap.put(taskId, stats);
    }

    @Test
    @DisplayName("场景4: 极限长上下文测试")
    void simulateExtremeLongContextTask() throws LlmException {
        String taskId = "extreme-context-" + System.currentTimeMillis();
        TaskTokenStats stats = new TaskTokenStats("极限长上下文测试");

        String veryLongCode = generateLongCodeSnippet(500);
        
        mockLlmClient.enqueueResponse(LlmResponseBuilder.create()
                .content("好的，我来分析这段" + veryLongCode.length() + "字符的代码...")
                .usage(8000, 500)
                .build());

        simulateConversation(taskId, stats, List.of(
                "请分析下面这段超长代码的性能问题:\n" + veryLongCode
        ));

        statsMap.put(taskId, stats);
    }

    private void simulateConversation(String taskId, TaskTokenStats stats, List<String> userInputs) throws LlmException {
        logger.info("开始模拟任务: {}", stats.taskName);
        conversationHistory.clear();
        conversationHistory.add(Message.system("You are a helpful AI assistant."));

        int round = 0;
        for (String userInput : userInputs) {
            round++;
            
            int inputTokens = tokenEstimator.estimateTextTokens(userInput);
            stats.totalInputTokens += inputTokens;
            
            conversationHistory.add(Message.user(userInput));
            
            int historyTokens = tokenEstimator.estimateConversationTokens(conversationHistory);
            stats.maxContextTokens = Math.max(stats.maxContextTokens, historyTokens);

            ChatResponse response = mockLlmClient.chat(conversationHistory);
            Usage usage = response.getUsage();
            
            stats.totalLlmCalls++;
            stats.totalPromptTokens += usage.getPromptTokens();
            stats.totalCompletionTokens += usage.getCompletionTokens();
            
            if (response.hasToolCalls()) {
                Message msg = response.getFirstMessage();
                if (msg != null && msg.getToolCalls() != null) {
                    stats.toolCallCount += msg.getToolCalls().size();
                }
            }

            Message responseMsg = response.getFirstMessage();
            if (responseMsg != null) {
                conversationHistory.add(responseMsg);
            }

            logger.info("  第{}轮: prompt={}, completion={}, context={}",
                    round, usage.getPromptTokens(), usage.getCompletionTokens(), historyTokens);
        }

        stats.totalRounds = round;
        stats.totalTokens = stats.totalPromptTokens + stats.totalCompletionTokens;
        
        metricsCollector.recordConversation(
                taskId, LocalDateTime.now(),
                tokenEstimator.estimateTextTokens(String.join("", userInputs)),
                createUsage(stats.totalPromptTokens, stats.totalCompletionTokens)
        );

        logger.info("任务完成: {} 轮对话, 总Token: {}", round, stats.totalTokens);
    }

    private List<ChatResponse> generateRefactoringResponseSequence() {
        List<ChatResponse> responses = new ArrayList<>();
        
        responses.add(LlmResponseBuilder.create()
                .content("我来分析当前的错误处理结构。首先让我查看几个关键文件...")
                .usage(1200, 150)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .addToolCall(readFileToolCall("src/main/java/com/example/exception/GlobalExceptionHandler.java"))
                .usage(1500, 300)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .content("发现了以下问题：\n1. 异常层级不清晰\n2. 错误码分散\n3. 缺少统一重试机制")
                .usage(2000, 500)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .addToolCall(writeFileToolCall("src/main/java/com/example/exception/ErrorCodes.java", 
                        "public enum ErrorCodes { /* 50行错误码定义 */ }"))
                .usage(2500, 800)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .content("重构完成！共修改了8个文件，添加了统一异常处理、重试机制和标准化错误码。")
                .usage(3000, 600)
                .build());

        return responses;
    }

    private List<ChatResponse> generateDebuggingResponseSequence() {
        List<ChatResponse> responses = new ArrayList<>();
        
        responses.add(LlmResponseBuilder.create()
                .content("开始分析Bug，首先查看相关日志和代码...")
                .usage(800, 100)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .toolCalls(List.of(
                        grepToolCall("NullPointerException"),
                        readFileToolCall("src/main/java/com/example/service/PaymentService.java")
                ))
                .usage(1800, 400)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .addToolCall(bashToolCall("jstack 12345 | grep -A 20 BLOCKED"))
                .usage(2500, 200)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .content("根因分析：死锁发生在数据库连接池和Redis连接的获取顺序上...")
                .usage(3500, 800)
                .build());
        
        responses.add(LlmResponseBuilder.create()
                .content("修复方案：\n1. 统一资源获取顺序\n2. 添加连接超时\n3. 优化锁粒度")
                .usage(4000, 700)
                .build());

        return responses;
    }

    private List<ChatResponse> generateFeatureDevResponseSequence() {
        List<ChatResponse> responses = new ArrayList<>();
        
        String[] files = {"Role.java", "User.java", "RoleRepository.java", 
                "AuthService.java", "SecurityConfig.java", "AuthController.java"};
        
        responses.add(LlmResponseBuilder.create()
                .content("开始设计RBAC权限模型...")
                .usage(1000, 200)
                .build());
        
        for (String file : files) {
            responses.add(LlmResponseBuilder.create()
                    .addToolCall(writeFileToolCall("src/main/java/com/example/auth/" + file, 
                            "// " + file + " implementation\n class " + file.replace(".java", "") + " {}"))
                    .usage(2000 + files.length * 200, 600)
                    .build());
        }
        
        responses.add(LlmResponseBuilder.create()
                .content("权限管理功能开发完成！包含：\n- RBAC模型\n- JWT认证\n- 10个权限接口")
                .usage(5000, 400)
                .build());

        return responses;
    }

    private String generateLongCodeSnippet(int lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            sb.append("    public void method").append(i).append("() {\n");
            sb.append("        processData").append(i).append("();\n");
            sb.append("    }\n");
        }
        return sb.toString();
    }

    private Usage createUsage(int prompt, int completion) {
        Usage usage = new Usage();
        usage.setPromptTokens(prompt);
        usage.setCompletionTokens(completion);
        usage.setTotalTokens(prompt + completion);
        return usage;
    }

    private void printSummaryReport() {
        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    📊 复杂任务Token消耗模拟报告                       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  注: 所有数据为模拟统计，未消耗任何真实API Token                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        for (TaskTokenStats stats : statsMap.values()) {
            System.out.printf("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━%n");
            System.out.printf("  📋 任务名称:    %s%n", stats.taskName);
            System.out.printf("  🔄 对话轮数:    %d 轮%n", stats.totalRounds);
            System.out.printf("  🤖 LLM调用:     %d 次%n", stats.totalLlmCalls);
            System.out.printf("  🔧 工具调用:    %d 次%n", stats.toolCallCount);
            System.out.printf("  📈 峰值上下文:  %,d tokens%n", stats.maxContextTokens);
            System.out.printf("  📥 总输入Token: %,d%n", stats.totalInputTokens);
            System.out.printf("  📤 Prompt:      %,d tokens%n", stats.totalPromptTokens);
            System.out.printf("  📥 Completion:  %,d tokens%n", stats.totalCompletionTokens);
            System.out.printf("  💰 总计Token:   %,d tokens%n", stats.totalTokens);
            System.out.printf("  💲 预估成本:    $%.4f (GPT-4)%n", stats.totalTokens * 0.00003);
            System.out.println();
        }

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        long grandTotal = statsMap.values().stream().mapToLong(s -> s.totalTokens).sum();
        System.out.printf("  🎯 所有任务总消耗:  %,d tokens%n", grandTotal);
        System.out.printf("  💲 预估总成本:     $%.4f%n", grandTotal * 0.00003);
        System.out.println();
    }

    private static class TaskTokenStats {
        String taskName;
        int totalRounds;
        int totalLlmCalls;
        int toolCallCount;
        int maxContextTokens;
        int totalInputTokens;
        int totalPromptTokens;
        int totalCompletionTokens;
        long totalTokens;

        TaskTokenStats(String taskName) {
            this.taskName = taskName;
        }
    }
}
