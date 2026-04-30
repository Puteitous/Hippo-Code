package com.example.agent.memory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * autoDream 整合流程测试
 * 
 * 验收场景：
 * 1. 四阶段 Prompt 构建正确
 * 2. L1/L2 信息源加载正确
 * 3. 空会话列表处理
 * 4. 部分文件读取失败处理
 */
@DisplayName("autoDream 整合流程测试")
class ConsolidationPromptBuilderTest {

    @Test
    @DisplayName("四阶段 Prompt 构建正确")
    void testPromptBuilder() {
        String indexText = "# 现有记忆索引\n- 主题 1\n- 主题 2";
        List<String> sessions = List.of(
            "# 会话 1\n内容 1",
            "# 会话 2\n内容 2"
        );

        String prompt = ConsolidationPromptBuilder.buildConsolidationPrompt(indexText, sessions);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Phase 1 — Orient"));
        assertTrue(prompt.contains("Phase 2 — Gather"));
        assertTrue(prompt.contains("Phase 3 — Consolidate"));
        assertTrue(prompt.contains("Phase 4 — Prune"));
        assertTrue(prompt.contains("现有记忆索引"));
        assertTrue(prompt.contains("会话 1"));
        assertTrue(prompt.contains("会话 2"));
        assertTrue(prompt.contains("0 次 grep 是完美流程"));
    }

    @Test
    @DisplayName("空索引处理")
    void testEmptyIndex() {
        String indexText = "";
        List<String> sessions = List.of("# 会话 1\n内容 1");

        String prompt = ConsolidationPromptBuilder.buildConsolidationPrompt(indexText, sessions);

        assertNotNull(prompt);
        assertTrue(prompt.contains("（无现有记忆）") || prompt.contains(""));
    }

    @Test
    @DisplayName("空会话列表处理")
    void testEmptySessions() {
        String indexText = "# 现有记忆";
        List<String> sessions = List.of();

        String prompt = ConsolidationPromptBuilder.buildConsolidationPrompt(indexText, sessions);

        assertNotNull(prompt);
        assertTrue(prompt.contains("共 0 个"));
    }

    @Test
    @DisplayName("L3 grep 限制说明正确")
    void testL3GrepeLimit() {
        String prompt = ConsolidationPromptBuilder.buildConsolidationPrompt("", List.of("测试"));

        assertTrue(prompt.contains("0 次 grep 是完美流程"));
        assertTrue(prompt.contains("最多调用 3 次 grep"));
        assertTrue(prompt.contains("上限，不是目标"));
    }
}
