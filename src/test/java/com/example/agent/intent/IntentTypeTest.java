package com.example.agent.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class IntentTypeTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValueTests {

        @Test
        @DisplayName("所有意图类型都存在")
        void testAllIntentTypesExist() {
            IntentType[] types = IntentType.values();
            
            assertEquals(9, types.length);
            assertNotNull(IntentType.CODE_GENERATION);
            assertNotNull(IntentType.CODE_MODIFICATION);
            assertNotNull(IntentType.CODE_REVIEW);
            assertNotNull(IntentType.DEBUGGING);
            assertNotNull(IntentType.FILE_OPERATION);
            assertNotNull(IntentType.PROJECT_ANALYSIS);
            assertNotNull(IntentType.QUESTION);
            assertNotNull(IntentType.CLARIFICATION);
            assertNotNull(IntentType.UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(IntentType.class)
        @DisplayName("所有类型都有显示名称")
        void testAllTypesHaveDisplayName(IntentType type) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(IntentType.class)
        @DisplayName("所有类型都有描述")
        void testAllTypesHaveDescription(IntentType type) {
            assertNotNull(type.getDescription());
            assertFalse(type.getDescription().isEmpty());
        }
    }

    @Nested
    @DisplayName("显示名称测试")
    class DisplayNameTests {

        @Test
        @DisplayName("CODE_GENERATION显示名称")
        void testCodeGenerationDisplayName() {
            assertEquals("代码生成", IntentType.CODE_GENERATION.getDisplayName());
        }

        @Test
        @DisplayName("CODE_MODIFICATION显示名称")
        void testCodeModificationDisplayName() {
            assertEquals("代码修改", IntentType.CODE_MODIFICATION.getDisplayName());
        }

        @Test
        @DisplayName("CODE_REVIEW显示名称")
        void testCodeReviewDisplayName() {
            assertEquals("代码审查", IntentType.CODE_REVIEW.getDisplayName());
        }

        @Test
        @DisplayName("DEBUGGING显示名称")
        void testDebuggingDisplayName() {
            assertEquals("调试问题", IntentType.DEBUGGING.getDisplayName());
        }

        @Test
        @DisplayName("FILE_OPERATION显示名称")
        void testFileOperationDisplayName() {
            assertEquals("文件操作", IntentType.FILE_OPERATION.getDisplayName());
        }

        @Test
        @DisplayName("PROJECT_ANALYSIS显示名称")
        void testProjectAnalysisDisplayName() {
            assertEquals("项目分析", IntentType.PROJECT_ANALYSIS.getDisplayName());
        }

        @Test
        @DisplayName("QUESTION显示名称")
        void testQuestionDisplayName() {
            assertEquals("一般问题", IntentType.QUESTION.getDisplayName());
        }

        @Test
        @DisplayName("CLARIFICATION显示名称")
        void testClarificationDisplayName() {
            assertEquals("澄清确认", IntentType.CLARIFICATION.getDisplayName());
        }

        @Test
        @DisplayName("UNKNOWN显示名称")
        void testUnknownDisplayName() {
            assertEquals("未知意图", IntentType.UNKNOWN.getDisplayName());
        }
    }

    @Nested
    @DisplayName("描述测试")
    class DescriptionTests {

        @Test
        @DisplayName("CODE_GENERATION描述")
        void testCodeGenerationDescription() {
            assertEquals("用户请求生成新代码", IntentType.CODE_GENERATION.getDescription());
        }

        @Test
        @DisplayName("CODE_MODIFICATION描述")
        void testCodeModificationDescription() {
            assertEquals("用户请求修改现有代码", IntentType.CODE_MODIFICATION.getDescription());
        }

        @Test
        @DisplayName("DEBUGGING描述")
        void testDebuggingDescription() {
            assertEquals("用户遇到错误需要帮助调试", IntentType.DEBUGGING.getDescription());
        }

        @Test
        @DisplayName("UNKNOWN描述")
        void testUnknownDescription() {
            assertEquals("无法识别的意图", IntentType.UNKNOWN.getDescription());
        }
    }

    @Nested
    @DisplayName("requiresToolCall测试")
    class RequiresToolCallTests {

        @Test
        @DisplayName("CODE_GENERATION需要工具调用")
        void testCodeGenerationRequiresToolCall() {
            assertTrue(IntentType.CODE_GENERATION.requiresToolCall());
        }

        @Test
        @DisplayName("CODE_MODIFICATION需要工具调用")
        void testCodeModificationRequiresToolCall() {
            assertTrue(IntentType.CODE_MODIFICATION.requiresToolCall());
        }

        @Test
        @DisplayName("FILE_OPERATION需要工具调用")
        void testFileOperationRequiresToolCall() {
            assertTrue(IntentType.FILE_OPERATION.requiresToolCall());
        }

        @Test
        @DisplayName("PROJECT_ANALYSIS需要工具调用")
        void testProjectAnalysisRequiresToolCall() {
            assertTrue(IntentType.PROJECT_ANALYSIS.requiresToolCall());
        }

        @Test
        @DisplayName("DEBUGGING需要工具调用")
        void testDebuggingRequiresToolCall() {
            assertTrue(IntentType.DEBUGGING.requiresToolCall());
        }

        @Test
        @DisplayName("CODE_REVIEW不需要工具调用")
        void testCodeReviewDoesNotRequireToolCall() {
            assertFalse(IntentType.CODE_REVIEW.requiresToolCall());
        }

        @Test
        @DisplayName("QUESTION不需要工具调用")
        void testQuestionDoesNotRequireToolCall() {
            assertFalse(IntentType.QUESTION.requiresToolCall());
        }

        @Test
        @DisplayName("CLARIFICATION不需要工具调用")
        void testClarificationDoesNotRequireToolCall() {
            assertFalse(IntentType.CLARIFICATION.requiresToolCall());
        }

        @Test
        @DisplayName("UNKNOWN不需要工具调用")
        void testUnknownDoesNotRequireToolCall() {
            assertFalse(IntentType.UNKNOWN.requiresToolCall());
        }
    }

    @Nested
    @DisplayName("requiresCodeAnalysis测试")
    class RequiresCodeAnalysisTests {

        @Test
        @DisplayName("CODE_REVIEW需要代码分析")
        void testCodeReviewRequiresCodeAnalysis() {
            assertTrue(IntentType.CODE_REVIEW.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("DEBUGGING需要代码分析")
        void testDebuggingRequiresCodeAnalysis() {
            assertTrue(IntentType.DEBUGGING.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("PROJECT_ANALYSIS需要代码分析")
        void testProjectAnalysisRequiresCodeAnalysis() {
            assertTrue(IntentType.PROJECT_ANALYSIS.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("CODE_GENERATION不需要代码分析")
        void testCodeGenerationDoesNotRequireCodeAnalysis() {
            assertFalse(IntentType.CODE_GENERATION.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("CODE_MODIFICATION不需要代码分析")
        void testCodeModificationDoesNotRequireCodeAnalysis() {
            assertFalse(IntentType.CODE_MODIFICATION.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("FILE_OPERATION不需要代码分析")
        void testFileOperationDoesNotRequireCodeAnalysis() {
            assertFalse(IntentType.FILE_OPERATION.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("QUESTION不需要代码分析")
        void testQuestionDoesNotRequireCodeAnalysis() {
            assertFalse(IntentType.QUESTION.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("CLARIFICATION不需要代码分析")
        void testClarificationDoesNotRequireCodeAnalysis() {
            assertFalse(IntentType.CLARIFICATION.requiresCodeAnalysis());
        }

        @Test
        @DisplayName("UNKNOWN不需要代码分析")
        void testUnknownDoesNotRequireCodeAnalysis() {
            assertFalse(IntentType.UNKNOWN.requiresCodeAnalysis());
        }
    }

    @Nested
    @DisplayName("valueOf测试")
    class ValueOfTests {

        @Test
        @DisplayName("通过名称获取枚举值")
        void testValueOf() {
            assertEquals(IntentType.CODE_GENERATION, IntentType.valueOf("CODE_GENERATION"));
            assertEquals(IntentType.DEBUGGING, IntentType.valueOf("DEBUGGING"));
            assertEquals(IntentType.UNKNOWN, IntentType.valueOf("UNKNOWN"));
        }

        @Test
        @DisplayName("无效名称抛出异常")
        void testValueOfInvalid() {
            assertThrows(IllegalArgumentException.class, () -> {
                IntentType.valueOf("INVALID_TYPE");
            });
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @Test
        @DisplayName("枚举顺序")
        void testEnumOrder() {
            IntentType[] types = IntentType.values();
            
            assertEquals(IntentType.CODE_GENERATION, types[0]);
            assertEquals(IntentType.CODE_MODIFICATION, types[1]);
            assertEquals(IntentType.CODE_REVIEW, types[2]);
            assertEquals(IntentType.DEBUGGING, types[3]);
            assertEquals(IntentType.FILE_OPERATION, types[4]);
            assertEquals(IntentType.PROJECT_ANALYSIS, types[5]);
            assertEquals(IntentType.QUESTION, types[6]);
            assertEquals(IntentType.CLARIFICATION, types[7]);
            assertEquals(IntentType.UNKNOWN, types[8]);
        }

        @Test
        @DisplayName("枚举比较")
        void testEnumComparison() {
            assertSame(IntentType.CODE_GENERATION, IntentType.CODE_GENERATION);
            assertNotSame(IntentType.CODE_GENERATION, IntentType.CODE_MODIFICATION);
            assertNotEquals(IntentType.CODE_GENERATION, IntentType.CODE_MODIFICATION);
        }
    }
}
