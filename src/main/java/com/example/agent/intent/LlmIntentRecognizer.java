package com.example.agent.intent;

import com.example.agent.core.ThinkingContext;
import com.example.agent.core.ThinkingEngine;
import com.example.agent.llm.client.LlmClient;
import com.example.agent.llm.exception.LlmException;
import com.example.agent.llm.model.ChatResponse;
import com.example.agent.llm.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LlmIntentRecognizer implements IntentRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(LlmIntentRecognizer.class);

    private static final Set<String> INTENT_TOOLS = Set.of(
            "glob", "grep", "list_directory"
    );
    private static final int MAX_THINKING_ROUNDS = 2;

    private static final String LEGACY_INTENT_PROMPT = """
        你是一个意图识别助手。请分析用户的输入，识别其意图类型。
        
        可用的意图类型：
        - CODE_GENERATION: 代码生成（用户请求生成新代码）
        - CODE_MODIFICATION: 代码修改（用户请求修改现有代码）
        - CODE_REVIEW: 代码审查（用户请求审查或分析代码）
        - DEBUGGING: 调试问题（用户遇到错误需要帮助调试）
        - FILE_OPERATION: 文件操作（用户请求文件读写操作）
        - PROJECT_ANALYSIS: 项目分析（用户请求分析项目结构或代码库）
        - QUESTION: 一般问题（用户提出一般性问题）
        - UNKNOWN: 未知意图
        
        请以 JSON 格式返回结果，格式如下：
        {
            "intent_type": "意图类型",
            "confidence": 0.0-1.0之间的置信度,
            "entities": {
                "target_file": "目标文件路径（如果有）",
                "language": "编程语言（如果有）",
                "operation": "具体操作（如果有）"
            },
            "reasoning": "判断理由"
        }
        
        只返回 JSON，不要有其他内容。
        """;

    private static final String ENHANCED_INTENT_PROMPT = """
        你是一个意图识别专家。请分析用户的输入，准确识别其意图类型。
        
        === 重要：识别前可以探索代码库 ===
        
        如果你对用户提到的概念、文件、类名不了解，可以调用工具：
        - glob: 查找特定类型的文件（如 **/*Config.java）
        - grep: 在代码中搜索关键词
        - list_directory: 查看目录结构
        
        充分了解项目后，再输出最终的识别结果。
        
        可用的意图类型：
        - CODE_GENERATION: 代码生成（用户请求生成新代码）
        - CODE_MODIFICATION: 代码修改（用户请求修改现有代码）
        - CODE_REVIEW: 代码审查（用户请求审查或分析代码）
        - DEBUGGING: 调试问题（用户遇到错误需要帮助调试）
        - FILE_OPERATION: 文件操作（用户请求文件读写操作）
        - PROJECT_ANALYSIS: 项目分析（用户请求分析项目结构或代码库）
        - QUESTION: 一般问题（用户提出一般性问题）
        - UNKNOWN: 未知意图
        
        请以 JSON 格式返回结果，格式如下：
        {
            "intent_type": "意图类型",
            "confidence": 0.0-1.0之间的置信度,
            "entities": {
                "target_file": "目标文件路径（如果有）",
                "language": "编程语言（如果有）",
                "operation": "具体操作（如果有）"
            },
            "reasoning": "判断理由"
        }
        
        只返回 JSON，不要有其他内容。
        """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final RuleBasedIntentRecognizer fallbackRecognizer;
    private ThinkingEngine thinkingEngine;
    private boolean enabled = true;
    private boolean useThinkingEngine = true;

    public LlmIntentRecognizer(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        this.fallbackRecognizer = new RuleBasedIntentRecognizer();
    }

    public LlmIntentRecognizer(LlmClient llmClient, ThinkingEngine thinkingEngine) {
        this.llmClient = llmClient;
        this.thinkingEngine = thinkingEngine;
        this.objectMapper = new ObjectMapper();
        this.fallbackRecognizer = new RuleBasedIntentRecognizer();
    }

    public void setThinkingEngine(ThinkingEngine thinkingEngine) {
        this.thinkingEngine = thinkingEngine;
    }

    public void setUseThinkingEngine(boolean useThinkingEngine) {
        this.useThinkingEngine = useThinkingEngine;
    }

    @Override
    public IntentResult recognize(String userInput) {
        return recognize(userInput, null);
    }

    @Override
    public IntentResult recognize(String userInput, List<Message> context) {
        if (!enabled) {
            return fallbackRecognizer.recognize(userInput, context);
        }

        if (userInput == null || userInput.trim().isEmpty()) {
            return IntentResult.unknown();
        }

        if (useThinkingEngine && thinkingEngine != null) {
            return recognizeWithThinkingEngine(userInput, context);
        }
        return recognizeLegacy(userInput, context);
    }

    private IntentResult recognizeWithThinkingEngine(String userInput, List<Message> context) {
        logger.debug("使用 ThinkingEngine 进行意图识别，允许工具: {}", INTENT_TOOLS);

        ThinkingContext<IntentResult> thinkingContext = ThinkingContext.<IntentResult>builder()
                .systemPrompt(ENHANCED_INTENT_PROMPT)
                .userInput(userInput)
                .history(context)
                .allowedTools(INTENT_TOOLS)
                .maxRounds(MAX_THINKING_ROUNDS)
                .resultParser(this::parseIntentResult)
                .build();

        try {
            return thinkingEngine.think(thinkingContext);
        } catch (Exception e) {
            logger.warn("ThinkingEngine 意图识别失败，回退到传统模式: {}", e.getMessage());
            return recognizeLegacy(userInput, context);
        }
    }

    private IntentResult recognizeLegacy(String userInput, List<Message> context) {
        try {
            Message systemMessage = Message.system(LEGACY_INTENT_PROMPT);
            Message userMessage = Message.user(userInput);

            List<Message> messages = new ArrayList<>();
            messages.add(systemMessage);
            if (context != null) {
                messages.addAll(context);
            }
            messages.add(userMessage);

            ChatResponse response = llmClient.chat(messages);

            if (response != null && response.hasContent()) {
                String content = response.getFirstMessage().getContent();
                return parseIntentResult(content);
            }

            logger.warn("LLM 意图识别返回空响应，使用规则识别器");
            return fallbackRecognizer.recognize(userInput, context);

        } catch (LlmException e) {
            logger.warn("LLM 意图识别失败: {}, 使用规则识别器", e.getMessage());
            return fallbackRecognizer.recognize(userInput, context);
        } catch (Exception e) {
            logger.error("意图识别异常", e);
            return fallbackRecognizer.recognize(userInput, context);
        }
    }

    private IntentResult parseIntentResult(String content) {
        try {
            String jsonContent = extractJson(content);
            JsonNode root = objectMapper.readTree(jsonContent);

            String intentTypeStr = root.has("intent_type") ? root.get("intent_type").asText() : "UNKNOWN";
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 0.5;
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "";

            IntentType intentType = parseIntentType(intentTypeStr);

            Map<String, Object> entities = new HashMap<>();
            if (root.has("entities") && root.get("entities").isObject()) {
                JsonNode entitiesNode = root.get("entities");
                entitiesNode.fields().forEachRemaining(entry -> {
                    entities.put(entry.getKey(), entry.getValue().asText());
                });
            }

            return IntentResult.builder()
                    .type(intentType)
                    .confidence(confidence)
                    .entities(entities)
                    .reasoning(reasoning)
                    .build();

        } catch (Exception e) {
            logger.warn("解析意图识别结果失败: {}", e.getMessage());
            return IntentResult.unknown();
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private IntentType parseIntentType(String intentTypeStr) {
        try {
            return IntentType.valueOf(intentTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            for (IntentType type : IntentType.values()) {
                if (type.name().equalsIgnoreCase(intentTypeStr) ||
                    type.getDisplayName().equals(intentTypeStr)) {
                    return type;
                }
            }
            return IntentType.UNKNOWN;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
