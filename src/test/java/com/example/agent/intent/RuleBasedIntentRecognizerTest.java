package com.example.agent.intent;

import com.example.agent.llm.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedIntentRecognizerTest {

    private RuleBasedIntentRecognizer recognizer;

    @BeforeEach
    void setUp() {
        recognizer = new RuleBasedIntentRecognizer();
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", "  \n\t  "})
        @DisplayName("空输入和空白输入应返回UNKNOWN")
        void testNullOrEmptyInput(String input) {
            IntentResult result = recognizer.recognize(input);
            
            assertEquals(IntentType.UNKNOWN, result.getType());
            assertEquals(0.0, result.getConfidence());
        }

        @Test
        @DisplayName("超长输入应正常处理")
        void testVeryLongInput() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("写代码");
            }
            String longInput = sb.toString();
            
            IntentResult result = recognizer.recognize(longInput);
            
            assertNotNull(result);
            assertTrue(result.getConfidence() >= 0);
        }

        @Test
        @DisplayName("特殊字符输入应正常处理")
        void testSpecialCharacters() {
            String specialInput = "!@#$%^&*()_+-=[]{}|;':\",./<>?写代码";
            
            IntentResult result = recognizer.recognize(specialInput);
            
            assertNotNull(result);
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("Unicode字符输入应正常处理")
        void testUnicodeCharacters() {
            String unicodeInput = "写代码😀🎉💻";
            
            IntentResult result = recognizer.recognize(unicodeInput);
            
            assertNotNull(result);
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("单字符输入应正常处理")
        void testSingleCharacterInput() {
            IntentResult result = recognizer.recognize("写");
            
            assertNotNull(result);
            assertEquals(IntentType.QUESTION, result.getType());
            assertEquals(0.50, result.getConfidence());
        }
    }

    @Nested
    @DisplayName("代码生成模式测试")
    class CodeGenerationTests {

        @Test
        @DisplayName("匹配'写代码'")
        void testWriteCode() {
            IntentResult result = recognizer.recognize("帮我写一个排序算法的代码");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
            assertEquals("匹配代码生成模式", result.getReasoning());
        }

        @Test
        @DisplayName("匹配'生成函数'")
        void testGenerateFunction() {
            IntentResult result = recognizer.recognize("生成一个计算斐波那契数列的函数");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'创建类'")
        void testCreateClass() {
            IntentResult result = recognizer.recognize("创建一个用户管理类");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("匹配'实现方法'")
        void testImplementMethod() {
            IntentResult result = recognizer.recognize("实现一个二分查找方法");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("匹配'编写脚本'")
        void testWriteScript() {
            IntentResult result = recognizer.recognize("编写一个自动化部署脚本");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("匹配'开发模块'")
        void testDevelopModule() {
            IntentResult result = recognizer.recognize("开发一个日志模块");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Write a function",
            "CREATE a class",
            "IMPLEMENT a method"
        })
        @DisplayName("英文关键词大小写不敏感")
        void testCaseInsensitive(String input) {
            IntentResult result = recognizer.recognize(input);
            
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("代码修改模式测试")
    class CodeModificationTests {

        @Test
        @DisplayName("匹配'修改代码'")
        void testModifyCode() {
            IntentResult result = recognizer.recognize("修改这段代码的逻辑");
            
            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
            assertEquals(0.75, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'更新函数'")
        void testUpdateFunction() {
            IntentResult result = recognizer.recognize("更新这个函数的实现");
            
            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
        }

        @Test
        @DisplayName("匹配'重构类'")
        void testRefactorClass() {
            IntentResult result = recognizer.recognize("重构这个类的设计");
            
            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
        }

        @Test
        @DisplayName("匹配'优化代码'")
        void testOptimizeCode() {
            IntentResult result = recognizer.recognize("优化这个函数的性能");
            
            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
        }

        @Test
        @DisplayName("匹配'修复bug'")
        void testFixBug() {
            IntentResult result = recognizer.recognize("修复这个函数的逻辑");
            
            assertEquals(IntentType.CODE_MODIFICATION, result.getType());
        }
    }

    @Nested
    @DisplayName("调试问题模式测试")
    class DebuggingTests {

        @Test
        @DisplayName("匹配'错误'")
        void testError() {
            IntentResult result = recognizer.recognize("我的程序报错了");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'异常'")
        void testException() {
            IntentResult result = recognizer.recognize("抛出了一个异常");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
        }

        @Test
        @DisplayName("匹配'bug'")
        void testBug() {
            IntentResult result = recognizer.recognize("这里有个bug");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
        }

        @Test
        @DisplayName("匹配'调试'")
        void testDebug() {
            IntentResult result = recognizer.recognize("帮我调试这段代码");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
        }

        @Test
        @DisplayName("匹配'不工作'")
        void testNotWorking() {
            IntentResult result = recognizer.recognize("这个功能不工作了");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
        }

        @Test
        @DisplayName("匹配'失败'")
        void testFailed() {
            IntentResult result = recognizer.recognize("测试失败了");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
        }
    }

    @Nested
    @DisplayName("文件操作模式测试")
    class FileOperationTests {

        @Test
        @DisplayName("匹配'读取文件'")
        void testReadFile() {
            IntentResult result = recognizer.recognize("读取配置文件");
            
            assertEquals(IntentType.FILE_OPERATION, result.getType());
            assertEquals(0.70, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'写入文件'")
        void testWriteFile() {
            IntentResult result = recognizer.recognize("写入日志文件");
            
            assertEquals(IntentType.FILE_OPERATION, result.getType());
        }

        @Test
        @DisplayName("匹配'删除文件'")
        void testDeleteFile() {
            IntentResult result = recognizer.recognize("删除临时文件");
            
            assertEquals(IntentType.FILE_OPERATION, result.getType());
        }

        @Test
        @DisplayName("匹配'查看目录'")
        void testViewDirectory() {
            IntentResult result = recognizer.recognize("查看当前目录");
            
            assertEquals(IntentType.FILE_OPERATION, result.getType());
        }
    }

    @Nested
    @DisplayName("项目分析模式测试")
    class ProjectAnalysisTests {

        @Test
        @DisplayName("匹配'分析项目'")
        void testAnalyzeProject() {
            IntentResult result = recognizer.recognize("分析这个项目的结构");
            
            assertEquals(IntentType.PROJECT_ANALYSIS, result.getType());
            assertEquals(0.70, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'了解代码库'")
        void testUnderstandCodebase() {
            IntentResult result = recognizer.recognize("了解这个代码库");
            
            assertEquals(IntentType.PROJECT_ANALYSIS, result.getType());
        }

        @Test
        @DisplayName("匹配'探索架构'")
        void testExploreArchitecture() {
            IntentResult result = recognizer.recognize("探索项目架构");
            
            assertEquals(IntentType.PROJECT_ANALYSIS, result.getType());
        }
    }

    @Nested
    @DisplayName("代码审查模式测试")
    class CodeReviewTests {

        @Test
        @DisplayName("匹配'审查代码'")
        void testReviewCode() {
            IntentResult result = recognizer.recognize("审查这段代码");
            
            assertEquals(IntentType.CODE_REVIEW, result.getType());
            assertEquals(0.60, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'检查实现'")
        void testCheckImplementation() {
            IntentResult result = recognizer.recognize("检查这个实现");
            
            assertEquals(IntentType.CODE_REVIEW, result.getType());
        }
    }

    @Nested
    @DisplayName("一般问题模式测试")
    class QuestionTests {

        @Test
        @DisplayName("匹配'什么是'")
        void testWhatIs() {
            IntentResult result = recognizer.recognize("什么是Java");
            
            assertEquals(IntentType.QUESTION, result.getType());
            assertEquals(0.75, result.getConfidence());
        }

        @Test
        @DisplayName("匹配'如何'")
        void testHowTo() {
            IntentResult result = recognizer.recognize("如何学习编程");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }

        @Test
        @DisplayName("匹配'为什么'")
        void testWhy() {
            IntentResult result = recognizer.recognize("为什么需要接口");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }

        @Test
        @DisplayName("匹配'怎么'")
        void testHow() {
            IntentResult result = recognizer.recognize("怎么安装Maven");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }

        @Test
        @DisplayName("匹配'解释'")
        void testExplain() {
            IntentResult result = recognizer.recognize("解释一下多线程");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }
    }

    @Nested
    @DisplayName("代码关键词匹配测试")
    class CodeKeywordTests {

        @Test
        @DisplayName("包含'函数'关键词的问句")
        void testFunctionKeyword() {
            IntentResult result = recognizer.recognize("这个函数怎么用");
            
            assertEquals(IntentType.QUESTION, result.getType());
            assertEquals(0.75, result.getConfidence());
            assertEquals("匹配一般问题模式", result.getReasoning());
        }

        @Test
        @DisplayName("包含'class'关键词的问句")
        void testClassKeyword() {
            IntentResult result = recognizer.recognize("请解释这个class的作用");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }

        @Test
        @DisplayName("包含'方法'关键词的问句")
        void testMethodKeyword() {
            IntentResult result = recognizer.recognize("这个方法怎么用");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }

        @Test
        @DisplayName("包含'interface'关键词的问句")
        void testInterfaceKeyword() {
            IntentResult result = recognizer.recognize("请解释这个interface");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }

        @Test
        @DisplayName("包含代码关键词但无问句模式")
        void testCodeKeywordWithoutQuestion() {
            IntentResult result = recognizer.recognize("变量命名规范");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.60, result.getConfidence());
        }
    }

    @Nested
    @DisplayName("默认分类测试")
    class DefaultClassificationTests {

        @Test
        @DisplayName("无法匹配任何模式时默认为QUESTION")
        void testDefaultClassification() {
            IntentResult result = recognizer.recognize("今天天气很好");
            
            assertEquals(IntentType.QUESTION, result.getType());
            assertEquals(0.50, result.getConfidence());
            assertEquals("默认归类为一般问题", result.getReasoning());
        }

        @Test
        @DisplayName("随机字符串默认为QUESTION")
        void testRandomString() {
            IntentResult result = recognizer.recognize("asdfghjkl");
            
            assertEquals(IntentType.QUESTION, result.getType());
        }
    }

    @Nested
    @DisplayName("优先级测试")
    class PriorityTests {

        @Test
        @DisplayName("代码生成与问题多模式匹配")
        void testCodeGenerationPriority() {
            IntentResult result = recognizer.recognize("如何写一个函数");
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
            assertEquals(0.50, result.getConfidence());
            assertTrue(result.getReasoning().contains("多模式匹配"));
        }

        @Test
        @DisplayName("调试优先于代码修改")
        void testDebuggingPriority() {
            IntentResult result = recognizer.recognize("修改代码时出现错误");
            
            assertEquals(IntentType.DEBUGGING, result.getType());
            assertEquals(0.50, result.getConfidence());
            assertTrue(result.getReasoning().contains("多模式匹配"));
        }
    }

    @Nested
    @DisplayName("上下文参数测试")
    class ContextTests {

        @Test
        @DisplayName("带上下文的识别")
        void testWithContext() {
            List<Message> context = new ArrayList<>();
            context.add(Message.user("之前的问题"));
            
            IntentResult result = recognizer.recognize("写一个函数", context);
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("空上下文列表")
        void testEmptyContext() {
            IntentResult result = recognizer.recognize("写代码", new ArrayList<>());
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }

        @Test
        @DisplayName("null上下文")
        void testNullContext() {
            IntentResult result = recognizer.recognize("写代码", null);
            
            assertEquals(IntentType.CODE_GENERATION, result.getType());
        }
    }

    @Nested
    @DisplayName("接口方法测试")
    class InterfaceTests {

        @Test
        @DisplayName("isEnabled默认返回true")
        void testIsEnabled() {
            assertTrue(recognizer.isEnabled());
        }

        @Test
        @DisplayName("getName返回类名")
        void testGetName() {
            assertEquals("RuleBasedIntentRecognizer", recognizer.getName());
        }
    }

    @Nested
    @DisplayName("置信度边界测试")
    class ConfidenceBoundaryTests {

        @Test
        @DisplayName("代码生成置信度应为0.85")
        void testCodeGenerationConfidence() {
            IntentResult result = recognizer.recognize("写代码");
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("调试置信度应为0.85")
        void testDebuggingConfidence() {
            IntentResult result = recognizer.recognize("报错了");
            assertEquals(0.85, result.getConfidence());
        }

        @Test
        @DisplayName("代码修改置信度应为0.75")
        void testCodeModificationConfidence() {
            IntentResult result = recognizer.recognize("修改代码");
            assertEquals(0.75, result.getConfidence());
        }

        @Test
        @DisplayName("一般问题置信度应为0.75")
        void testQuestionConfidence() {
            IntentResult result = recognizer.recognize("什么是Java");
            assertEquals(0.75, result.getConfidence());
        }

        @Test
        @DisplayName("文件操作置信度应为0.70")
        void testFileOperationConfidence() {
            IntentResult result = recognizer.recognize("读取文件");
            assertEquals(0.70, result.getConfidence());
        }

        @Test
        @DisplayName("项目分析置信度应为0.70")
        void testProjectAnalysisConfidence() {
            IntentResult result = recognizer.recognize("分析项目");
            assertEquals(0.70, result.getConfidence());
        }

        @Test
        @DisplayName("代码审查置信度应为0.60")
        void testCodeReviewConfidence() {
            IntentResult result = recognizer.recognize("审查代码");
            assertEquals(0.60, result.getConfidence());
        }

        @Test
        @DisplayName("关键词匹配置信度应为0.60")
        void testKeywordMatchConfidence() {
            IntentResult result = recognizer.recognize("这个函数");
            assertEquals(0.60, result.getConfidence());
        }

        @Test
        @DisplayName("默认分类置信度应为0.50")
        void testDefaultConfidence() {
            IntentResult result = recognizer.recognize("随便说说");
            assertEquals(0.50, result.getConfidence());
        }

        @Test
        @DisplayName("多模式匹配置信度应为0.50")
        void testMultiMatchConfidence() {
            IntentResult result = recognizer.recognize("修改代码时出现错误");
            assertEquals(0.50, result.getConfidence());
            assertTrue(result.getReasoning().contains("多模式匹配"));
        }
    }
}
