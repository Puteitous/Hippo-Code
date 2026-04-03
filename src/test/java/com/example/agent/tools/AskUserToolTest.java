package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AskUserToolTest {

    private AskUserTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new AskUserTool();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetName() {
        assertEquals("ask_user", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("提问"));
        assertTrue(description.contains("回答"));
        assertTrue(description.contains("确认"));
    }

    @Test
    void testGetParametersSchema() {
        String schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("question"));
        assertTrue(schema.contains("options"));
        assertTrue(schema.contains("allow_custom_input"));
    }

    @Test
    void testMissingQuestionParameter() {
        ObjectNode args = objectMapper.createObjectNode();
        
        assertThrows(ToolExecutionException.class, () -> {
            tool.execute(args);
        });
    }

    @Test
    void testRequiresFileLock() {
        assertFalse(tool.requiresFileLock());
    }

    @Test
    void testGetAffectedPaths() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("question", "测试问题");
        
        var paths = tool.getAffectedPaths(args);
        
        assertNotNull(paths);
        assertTrue(paths.isEmpty());
    }

    @Test
    void testParameterSchemaFormat() {
        String schema = tool.getParametersSchema();
        
        assertTrue(schema.contains("\"type\": \"object\""));
        assertTrue(schema.contains("\"required\": [\"question\"]"));
        assertTrue(schema.contains("\"type\": \"string\""));
        assertTrue(schema.contains("\"type\": \"array\""));
        assertTrue(schema.contains("\"type\": \"boolean\""));
    }
}
