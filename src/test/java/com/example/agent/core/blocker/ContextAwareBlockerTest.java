package com.example.agent.core.blocker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 4: 上下文感知 Blocker 测试")
class ContextAwareBlockerTest {

    private ContextAwareBlocker blocker;

    @BeforeEach
    void setUp() {
        blocker = new ContextAwareBlocker();
    }

    @Nested
    @DisplayName("软引导行为测试")
    class SoftGuidanceTests {

        @Test
        @DisplayName("高风险操作应该始终允许执行（软引导）")
        void highRiskOperation_shouldAlwaysBeAllowed() {
            JsonNode args = JsonNodeFactory.instance.objectNode()
                    .put("command", "rm -rf /tmp/test");

            HookResult result = blocker.check("bash", args);

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("低风险操作应该允许执行")
        void lowRiskOperation_shouldBeAllowed() {
            JsonNode args = JsonNodeFactory.instance.objectNode()
                    .put("path", "src/test.txt");

            HookResult result = blocker.check("read_file", args);

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("连续高风险操作应该只记录日志，不拦截")
        void consecutiveHighRiskOps_shouldOnlyLog() {
            JsonNode bashArgs = JsonNodeFactory.instance.objectNode()
                    .put("command", "sudo chmod 777 /etc/config");
            JsonNode writeArgs = JsonNodeFactory.instance.objectNode()
                    .put("path", "/etc/config.env");

            for (int i = 0; i < 5; i++) {
                HookResult result1 = blocker.check("bash", bashArgs);
                assertTrue(result1.isAllowed());

                HookResult result2 = blocker.check("write_file", writeArgs);
                assertTrue(result2.isAllowed());
            }

            assertEquals(10, blocker.getTotalOperations());
            assertTrue(blocker.getConsecutiveHighRiskOps() > 0);
        }
    }

    @Nested
    @DisplayName("风险评分测试")
    class RiskScoreTests {

        @Test
        @DisplayName("应该能追踪总操作数")
        void shouldTrackTotalOperations() {
            JsonNode args = JsonNodeFactory.instance.objectNode();

            blocker.check("read_file", args);
            blocker.check("bash", args);
            blocker.check("edit_file", args);

            assertEquals(3, blocker.getTotalOperations());
        }

        @Test
        @DisplayName("重置后应该清空状态")
        void reset_shouldClearState() {
            JsonNode args = JsonNodeFactory.instance.objectNode()
                    .put("command", "sudo rm -rf /");

            blocker.check("bash", args);
            blocker.check("bash", args);

            blocker.reset();

            assertEquals(0, blocker.getTotalOperations());
            assertEquals(0, blocker.getConsecutiveHighRiskOps());
        }
    }
}
