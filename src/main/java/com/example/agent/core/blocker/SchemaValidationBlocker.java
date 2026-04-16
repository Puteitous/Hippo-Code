package com.example.agent.core.blocker;

import com.example.agent.tools.ToolExecutor;
import com.example.agent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SchemaValidationBlocker implements Blocker {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidationBlocker.class);
    
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Set<String>> requiredFieldsCache = new HashMap<>();

    public SchemaValidationBlocker(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        initializeSchemaCache();
    }

    private void initializeSchemaCache() {
        for (ToolExecutor tool : toolRegistry.getAllTools()) {
            Set<String> requiredFields = parseRequiredFields(tool.getParametersSchema());
            requiredFieldsCache.put(tool.getName(), requiredFields);
        }
    }

    private Set<String> parseRequiredFields(String schemaJson) {
        Set<String> required = new HashSet<>();
        try {
            if (schemaJson == null || schemaJson.trim().isEmpty()) {
                return required;
            }
            JsonNode schema = objectMapper.readTree(schemaJson);
            if (schema.has("required")) {
                for (JsonNode field : schema.get("required")) {
                    required.add(field.asText());
                }
            }
        } catch (Exception e) {
            logger.warn("解析 Schema 失败: {}", e.getMessage());
        }
        return required;
    }

    @Override
    public HookResult check(String toolName, JsonNode arguments) {
        Set<String> requiredFields = requiredFieldsCache.get(toolName);
        
        if (requiredFields == null || requiredFields.isEmpty()) {
            return HookResult.allow();
        }

        for (String field : requiredFields) {
            if (!arguments.has(field) || arguments.get(field).isNull()) {
                return HookResult.validationError(
                    String.format("缺少必需参数: %s", field),
                    String.format("正确示例: %s", getExampleForTool(toolName, field))
                );
            }
        }

        return HookResult.allow();
    }

    private String getExampleForTool(String toolName, String missingField) {
        Map<String, Map<String, String>> examples = getToolExamples();
        Map<String, String> toolExample = examples.get(toolName);
        
        if (toolExample != null) {
            return "{" + toolExample.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\": \"" + e.getValue() + "\"")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("") + "}";
        }
        
        return String.format("\"%s\": \"你的值\"", missingField);
    }

    private Map<String, Map<String, String>> getToolExamples() {
        Map<String, Map<String, String>> examples = new HashMap<>();
        
        examples.put("read_file", Map.of(
            "path", "src/main/java/com/example/Example.java"
        ));
        
        examples.put("edit_file", Map.of(
            "path", "src/main/java/com/example/Example.java",
            "old_text", "旧的代码内容",
            "new_text", "新的代码内容"
        ));
        
        examples.put("write_file", Map.of(
            "path", "src/main/java/com/example/Example.java",
            "content", "文件内容"
        ));
        
        examples.put("bash", Map.of(
            "command", "mvn compile -q",
            "timeout", "30"
        ));
        
        examples.put("glob", Map.of(
            "pattern", "**/*.java"
        ));
        
        examples.put("grep", Map.of(
            "pattern", "searchKeyword"
        ));
        
        examples.put("SearchCodebase", Map.of(
            "information_request", "查找用户认证相关的代码"
        ));
        
        return examples;
    }

    public Set<String> getRequiredFields(String toolName) {
        return requiredFieldsCache.getOrDefault(toolName, Collections.emptySet());
    }
}
