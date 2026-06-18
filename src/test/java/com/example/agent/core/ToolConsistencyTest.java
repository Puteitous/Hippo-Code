package com.example.agent.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Prompt 文件内容检查")
class ToolConsistencyTest {

    @Test
    @DisplayName("mode/coding.md 存在且包含关键内容")
    void codingModePromptExists() {
        String content = readResource("prompts/mode/coding.md");
        assertNotNull(content);
        assertTrue(content.contains("构建模式"), "应包含模式标识");
        assertTrue(content.contains("全权限"), "应说明全权限执行");
    }

    @Test
    @DisplayName("mode/chat.md 存在且包含关键内容")
    void chatModePromptExists() {
        String content = readResource("prompts/mode/chat.md");
        assertNotNull(content);
        assertTrue(content.contains("顾问模式"), "应包含模式标识");
        assertTrue(content.contains("只读"), "应说明只读限制");
    }

    @Test
    @DisplayName("core/role.md 存在且包含关键内容")
    void coreRolePromptExists() {
        String content = readResource("prompts/core/role.md");
        assertNotNull(content);
        assertTrue(content.contains("编程助手"), "应包含角色定义");
        assertTrue(content.contains("中文回复"), "应指定语言");
    }

    private String readResource(String resourcePath) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
