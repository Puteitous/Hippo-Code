package com.example.agent.execute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class AgentTurnResultTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValueTests {

        @Test
        @DisplayName("所有结果类型都存在")
        void testAllResultTypesExist() {
            AgentTurnResult[] results = AgentTurnResult.values();

            assertEquals(4, results.length);
            assertNotNull(AgentTurnResult.DONE);
            assertNotNull(AgentTurnResult.CONTINUE);
            assertNotNull(AgentTurnResult.EMPTY_RESPONSE);
            assertNotNull(AgentTurnResult.ERROR);
        }

        @ParameterizedTest
        @EnumSource(AgentTurnResult.class)
        @DisplayName("所有结果类型都有名称")
        void testAllResultsHaveName(AgentTurnResult result) {
            assertNotNull(result.name());
            assertFalse(result.name().isEmpty());
        }
    }

    @Nested
    @DisplayName("valueOf测试")
    class ValueOfTests {

        @Test
        @DisplayName("通过名称获取枚举值")
        void testValueOf() {
            assertEquals(AgentTurnResult.DONE, AgentTurnResult.valueOf("DONE"));
            assertEquals(AgentTurnResult.CONTINUE, AgentTurnResult.valueOf("CONTINUE"));
            assertEquals(AgentTurnResult.EMPTY_RESPONSE, AgentTurnResult.valueOf("EMPTY_RESPONSE"));
            assertEquals(AgentTurnResult.ERROR, AgentTurnResult.valueOf("ERROR"));
        }

        @Test
        @DisplayName("无效名称抛出异常")
        void testValueOfInvalid() {
            assertThrows(IllegalArgumentException.class, () -> {
                AgentTurnResult.valueOf("INVALID");
            });
        }
    }

    @Nested
    @DisplayName("枚举顺序测试")
    class EnumOrderTests {

        @Test
        @DisplayName("枚举顺序正确")
        void testEnumOrder() {
            AgentTurnResult[] results = AgentTurnResult.values();

            assertEquals(AgentTurnResult.DONE, results[0]);
            assertEquals(AgentTurnResult.CONTINUE, results[1]);
            assertEquals(AgentTurnResult.EMPTY_RESPONSE, results[2]);
            assertEquals(AgentTurnResult.ERROR, results[3]);
        }
    }

    @Nested
    @DisplayName("枚举比较测试")
    class EnumComparisonTests {

        @Test
        @DisplayName("相同枚举值相等")
        void testSameEnumEqual() {
            assertSame(AgentTurnResult.DONE, AgentTurnResult.DONE);
            assertEquals(AgentTurnResult.DONE, AgentTurnResult.DONE);
        }

        @Test
        @DisplayName("不同枚举值不相等")
        void testDifferentEnumNotEqual() {
            assertNotEquals(AgentTurnResult.DONE, AgentTurnResult.CONTINUE);
            assertNotEquals(AgentTurnResult.DONE, AgentTurnResult.ERROR);
            assertNotEquals(AgentTurnResult.CONTINUE, AgentTurnResult.EMPTY_RESPONSE);
        }
    }

    @Nested
    @DisplayName("语义测试")
    class SemanticTests {

        @Test
        @DisplayName("DONE表示对话完成")
        void testDoneSemantics() {
            AgentTurnResult result = AgentTurnResult.DONE;
            assertEquals("DONE", result.name());
        }

        @Test
        @DisplayName("CONTINUE表示需要继续对话")
        void testContinueSemantics() {
            AgentTurnResult result = AgentTurnResult.CONTINUE;
            assertEquals("CONTINUE", result.name());
        }

        @Test
        @DisplayName("EMPTY_RESPONSE表示空响应")
        void testEmptyResponseSemantics() {
            AgentTurnResult result = AgentTurnResult.EMPTY_RESPONSE;
            assertEquals("EMPTY_RESPONSE", result.name());
        }

        @Test
        @DisplayName("ERROR表示错误")
        void testErrorSemantics() {
            AgentTurnResult result = AgentTurnResult.ERROR;
            assertEquals("ERROR", result.name());
        }
    }
}
