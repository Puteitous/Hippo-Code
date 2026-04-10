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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.example.agent.testutil.TestFixtures.ToolCalls.bashToolCall;
import static com.example.agent.testutil.TestFixtures.ToolCalls.grepToolCall;
import static com.example.agent.testutil.TestFixtures.ToolCalls.readFileToolCall;
import static com.example.agent.testutil.TestFixtures.ToolCalls.writeFileToolCall;

@DisplayName("多模式Token消耗模拟测试")
public class TokenSimulationMultiModeTest {

    private static final Logger logger = LoggerFactory.getLogger(TokenSimulationMultiModeTest.class);

    public enum CalculationMode {
        BASELINE("基准模式-固定Token"),
        REAL_TOKENS("真实计算模式-Tiktoken"),
        REAL_WITH_VARIANCE("真实波动模式-±15%随机"),
        REAL_API("真实API模式-调用LLM");

        final String displayName;
        CalculationMode(String displayName) {
            this.displayName = displayName;
        }
    }

    @Test
    @DisplayName("🔥 一键对比：4模式同跑完整数据对比（推荐）")
    void compareAllFourModes() throws LlmException {
        logger.info("");
        logger.info("╔══════════════════════════════════════════════╗");
        logger.info("║       开始执行完整4模式对比评测               ║");
        logger.info("╚══════════════════════════════════════════════╝");
        logger.info("");

        allResults.clear();

        runFullSimulation(CalculationMode.BASELINE);
        runFullSimulation(CalculationMode.REAL_TOKENS);
        runFullSimulation(CalculationMode.REAL_WITH_VARIANCE);
        // =====================================================================
        // ⚠️  需要对比真实API时，取消下面这行注释即可
        // runFullSimulation(CalculationMode.REAL_API);
        // =====================================================================

        logger.info("");
        logger.info("✅  所有模式运行完成，查看下方对比表格");
        logger.info("");
    }

    @Nested
    @DisplayName("模式1: 基准测试 - 固定Token (用于回归对比)")
    class BaselineFixedTokenTest {
        @Test
        @DisplayName("完整场景 - 固定Token基准")
        void fullScenarioBaseline() throws LlmException {
            runFullSimulation(CalculationMode.BASELINE);
        }
    }

    @Nested
    @DisplayName("模式2: 真实Token计算 - Tiktoken精确统计")
    class RealTokenCalculationTest {
        @Test
        @DisplayName("完整场景 - 真实Token计算")
        void fullScenarioRealTokens() throws LlmException {
            runFullSimulation(CalculationMode.REAL_TOKENS);
        }
    }

    @Nested
    @DisplayName("模式3: 真实波动模式 - 模拟真实API波动")
    class RealVarianceTokenTest {
        @Test
        @DisplayName("完整场景 - 带随机波动模拟")
        void fullScenarioWithVariance() throws LlmException {
            runFullSimulation(CalculationMode.REAL_WITH_VARIANCE);
        }
    }

    @Nested
    @DisplayName("模式4: 真实API调用 - 端到端验证")
    @Disabled("默认关闭，需要手动启用。启用前请确保配置了正确的API Key，会产生真实费用！")
    class RealApiTest {
        /**
         * ⚠️  启用真实API模式说明：
         * 1. 移除类上的 @Disabled 注解
         * 2. 配置API Key：方式1：环境变量 OPENAI_API_KEY
         *                 方式2：修改 setUp() 中 config 的 apiKey/baseUrl
         * 3. 运行测试会产生真实API费用，请谨慎运行
         */
        @Test
        @DisplayName("完整场景 - 真实LLM调用（会产生真实费用）")
        @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
        void fullScenarioRealApi() throws LlmException {
            // =====================================================================
            // 👇 切换真实API只需这一行：注释掉Mock，用真实Client
            // mockLlmClient = new DefaultLlmClient(loadRealConfig());
            // =====================================================================
            
            runFullSimulation(CalculationMode.REAL_API);
        }
    }

    private MockLlmClient mockLlmClient;
    private TokenEstimator tokenEstimator;
    private TokenMetricsCollector metricsCollector;
    private Random random;
    private final Map<String, ScenarioResult> allResults = new HashMap<>();

    @BeforeEach
    void setUp() {
        // 注释掉Mock，换成真实Client
        // mockLlmClient = new MockLlmClient();
    
        // ✅ 真实API调用模式
        //mockLlmClient = new DefaultLlmClient()(config);
        // 加载你的真实API Key
        
        mockLlmClient = new MockLlmClient();
        tokenEstimator = new TiktokenEstimator();
        metricsCollector = new TokenMetricsCollector(LocalDate.now());
        random = new Random(42); 
        allResults.clear();
    }

    @AfterEach
    void tearDown() {
        if (!allResults.isEmpty()) {
            printComparisonReport();
        }
    }

    private void runFullSimulation(CalculationMode mode) throws LlmException {
        logger.info("========================================");
        logger.info("运行模式: {}", mode.displayName);
        logger.info("========================================");

        ScenarioResult result = new ScenarioResult(mode);

        runScenario("代码重构", result, generateRefactoringResponses(), List.of(
                "请帮我重构这个项目的错误处理模块，目前到处都是try-catch重复代码",
                "需要统一的异常层级结构",
                "要添加重试机制",
                "错误码需要标准化",
                "完成后请写单元测试"
        ));

        runScenario("Bug排查", result, generateDebuggingResponses(), List.of(
                "用户报告支付超时",
                "错误日志显示NullPointerException在PaymentService第456行",
                "数据库连接池经常耗尽",
                "高并发下出现死锁",
                "帮我分析根因并提供修复方案"
        ));

        runScenario("功能开发", result, generateFeatureDevResponses(), List.of(
                "我需要添加用户权限管理功能",
                "RBAC模型设计",
                "数据库表结构设计",
                "DAO层实现",
                "Service层实现",
                "添加JWT认证",
                "编写集成测试"
        ));

        runScenario("长上下文分析", result, List.of(
                LlmResponseBuilder.create().content("分析完成，发现3处性能瓶颈。").usage(8000, 500).build()
        ), List.of(generateLongCodeSnippet(800)));

        allResults.put(mode.name(), result);
    }

    private void runScenario(String scenarioName, ScenarioResult result,
                             List<ChatResponse> responses, List<String> userInputs) throws LlmException {
        mockLlmClient.reset();
        mockLlmClient.enqueueResponses(responses);

        List<Message> history = new ArrayList<>();
        history.add(Message.system("You are a helpful AI assistant."));

        ScenarioStats stats = new ScenarioStats(scenarioName);

        for (String userInput : userInputs) {
            history.add(Message.user(userInput));
            stats.inputTokens += tokenEstimator.estimateTextTokens(userInput);
            stats.maxContextTokens = Math.max(stats.maxContextTokens,
                    tokenEstimator.estimateConversationTokens(history));

            ChatResponse response = mockLlmClient.chat(history);
            stats.llmCalls++;

            if (response.hasToolCalls() && response.getFirstMessage() != null) {
                stats.toolCallCount += response.getFirstMessage().getToolCalls().size();
            }

            int promptTokens = calculatePromptTokens(history, response, result.mode);
            int completionTokens = calculateCompletionTokens(response, result.mode);

            stats.promptTokens += promptTokens;
            stats.completionTokens += completionTokens;

            Message msg = response.getFirstMessage();
            if (msg != null) {
                history.add(msg);
            }
        }

        stats.rounds = userInputs.size();
        stats.totalTokens = stats.promptTokens + stats.completionTokens;

        result.scenarios.add(stats);

        metricsCollector.recordConversation(
                scenarioName + "-" + result.mode, LocalDateTime.now(),
                stats.inputTokens,
                createUsage(stats.promptTokens, stats.completionTokens)
        );
    }

    private int calculatePromptTokens(List<Message> history, ChatResponse response, CalculationMode mode) {
        int baseline = response.getUsage().getPromptTokens();
        int real = tokenEstimator.estimateConversationTokens(history);

        return switch (mode) {
            case BASELINE -> baseline;
            case REAL_TOKENS -> real;
            case REAL_WITH_VARIANCE -> applyVariance(real);
            case REAL_API -> baseline;  // 真实API直接使用返回的Usage值
        };
    }

    private int calculateCompletionTokens(ChatResponse response, CalculationMode mode) {
        int baseline = response.getUsage().getCompletionTokens();
        Message msg = response.getFirstMessage();
        int real = msg != null && msg.getContent() != null
                ? tokenEstimator.estimateTextTokens(msg.getContent())
                : baseline;

        return switch (mode) {
            case BASELINE -> baseline;
            case REAL_TOKENS -> real;
            case REAL_WITH_VARIANCE -> applyVariance(real);
            case REAL_API -> baseline;  // 真实API直接使用返回的Usage值
        };
    }

    private int applyVariance(int value) {
        double variance = 0.85 + random.nextDouble() * 0.3; 
        return (int) (value * variance);
    }

    private List<ChatResponse> generateRefactoringResponses() {
        List<ChatResponse> responses = new ArrayList<>();
        responses.add(LlmResponseBuilder.create()
                .content("我来分析当前的错误处理结构。首先让我查看几个关键文件，发现异常处理分散在各个类中，缺少统一的层级结构。")
                .usage(1200, 150).build());
        responses.add(LlmResponseBuilder.create()
                .addToolCall(readFileToolCall("src/main/java/com/example/exception/GlobalExceptionHandler.java"))
                .usage(1500, 300).build());
        responses.add(LlmResponseBuilder.create()
                .content("发现了以下问题：1.异常层级不清晰 2.错误码分散 3.缺少统一重试机制。建议创建自定义异常层级。")
                .usage(2000, 500).build());
        responses.add(LlmResponseBuilder.create()
                .addToolCall(writeFileToolCall("src/main/java/com/example/exception/ErrorCodes.java",
                        "public enum ErrorCodes { PAYMENT_FAILED, AUTH_REQUIRED, RATE_LIMITED }"))
                .usage(2500, 800).build());
        responses.add(LlmResponseBuilder.create()
                .content("重构完成！共修改了8个文件，添加了统一异常处理、重试机制和标准化错误码。所有测试通过。")
                .usage(3000, 600).build());
        return responses;
    }

    private List<ChatResponse> generateDebuggingResponses() {
        List<ChatResponse> responses = new ArrayList<>();
        responses.add(LlmResponseBuilder.create()
                .content("开始分析Bug，首先查看相关日志和代码...让我搜索NullPointerException的相关代码。")
                .usage(800, 100).build());
        responses.add(LlmResponseBuilder.create()
                .toolCalls(List.of(grepToolCall("NullPointerException"),
                        readFileToolCall("src/main/java/com/example/service/PaymentService.java")))
                .usage(1800, 400).build());
        responses.add(LlmResponseBuilder.create()
                .addToolCall(bashToolCall("jstack 12345 | grep -A 20 BLOCKED"))
                .usage(2500, 200).build());
        responses.add(LlmResponseBuilder.create()
                .content("根因分析：死锁发生在数据库连接池和Redis连接的获取顺序上，线程A持有DB锁等待Redis锁，线程B持有Redis锁等待DB锁。")
                .usage(3500, 800).build());
        responses.add(LlmResponseBuilder.create()
                .content("修复方案：1.统一资源获取顺序 2.添加连接超时 3.优化锁粒度 4.添加死锁检测线程")
                .usage(4000, 700).build());
        return responses;
    }

    private List<ChatResponse> generateFeatureDevResponses() {
        List<ChatResponse> responses = new ArrayList<>();
        String[] files = {"Role.java", "User.java", "RoleRepository.java", "AuthService.java", "SecurityConfig.java"};
        responses.add(LlmResponseBuilder.create()
                .content("开始设计RBAC权限模型：用户-角色-权限三级结构，支持动态权限分配。")
                .usage(1000, 200).build());
        for (String file : files) {
            responses.add(LlmResponseBuilder.create()
                    .addToolCall(writeFileToolCall("src/main/java/com/example/auth/" + file,
                            "// JPA Entity with auditing, contains fields: id, name, permissions"))
                    .usage(2000, 600).build());
        }
        responses.add(LlmResponseBuilder.create()
                .content("权限管理功能开发完成！包含RBAC模型、JWT认证、10个REST接口，附带Swagger文档。")
                .usage(5000, 400).build());
        return responses;
    }

    private String generateLongCodeSnippet(int lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            sb.append("    public void processData").append(i).append("(List<String> items) {\n");
            sb.append("        for (int j = 0; j < items.size(); j++) {\n");
            sb.append("            validateItem(items.get(j));\n");
            sb.append("            transformItem(items.get(j));\n");
            sb.append("            persistItem(items.get(j));\n");
            sb.append("        }\n");
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

    private void printComparisonReport() {
        System.out.println("\n\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        📊 多模式Token消耗对比分析报告                               ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  模式说明:                                                                         ║");
        System.out.println("║  • 基准模式: 固定预设值，回归对比   • 真实计算: Tiktoken精确计算                    ║");
        System.out.println("║  • 真实波动: 真实计算±15%随机       • 真实API: 需手动启用，产生真实费用             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("┌──────────────┬─────────────────┬─────────────────┬─────────────────┬─────────────────┐");
        System.out.printf("│ %-12s │ %-15s │ %-15s │ %-15s │ %-15s │%n", "场景", "基准模式", "真实计算", "真实波动", "真实API");
        System.out.println("├──────────────┼─────────────────┼─────────────────┼─────────────────┼─────────────────┤");

        String[] scenarios = {"代码重构", "Bug排查", "功能开发", "长上下文分析"};
        for (String scenario : scenarios) {
            System.out.printf("│ %-12s │", scenario);
            for (CalculationMode mode : CalculationMode.values()) {
                ScenarioResult result = allResults.get(mode.name());
                if (result != null) {
                    ScenarioStats stats = result.scenarios.stream()
                            .filter(s -> s.name.equals(scenario))
                            .findFirst().orElse(null);
                    if (stats != null) {
                        System.out.printf(" %,8d tokens │", stats.totalTokens);
                    }
                }
            }
            System.out.println();
        }

        System.out.println("├──────────────┼─────────────────┼─────────────────┼─────────────────┼─────────────────┤");
        System.out.printf("│ %-12s │", "GPT-4成本");
        for (CalculationMode mode : CalculationMode.values()) {
            ScenarioResult result = allResults.get(mode.name());
            if (result != null) {
                long total = result.scenarios.stream().mapToLong(s -> s.totalTokens).sum();
                System.out.printf("   $%.4f      │", total * 0.00003);
            } else if (mode == CalculationMode.REAL_API) {
                System.out.printf("  %-15s │", "(默认关闭)");
            }
        }
        System.out.println();
        System.out.println("└──────────────┴───────────────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("📌 详细指标 - 真实计算模式:");
        System.out.println("──────────────────────────────────────────────────────────────────────");
        ScenarioResult realResult = allResults.get(CalculationMode.REAL_TOKENS.name());
        if (realResult != null) {
            for (ScenarioStats stats : realResult.scenarios) {
                System.out.printf("  ✅ %-16s | %2d轮 | %3dK上下文峰值 | %,8d总%n",
                        stats.name, stats.rounds, stats.maxContextTokens / 1000, stats.totalTokens);
            }
        }
        System.out.println();
    }

    private static class ScenarioResult {
        final CalculationMode mode;
        final List<ScenarioStats> scenarios = new ArrayList<>();

        ScenarioResult(CalculationMode mode) {
            this.mode = mode;
        }
    }

    private static class ScenarioStats {
        final String name;
        int rounds;
        int llmCalls;
        int toolCallCount;
        int maxContextTokens;
        int inputTokens;
        int promptTokens;
        int completionTokens;
        long totalTokens;

        ScenarioStats(String name) {
            this.name = name;
        }
    }
}