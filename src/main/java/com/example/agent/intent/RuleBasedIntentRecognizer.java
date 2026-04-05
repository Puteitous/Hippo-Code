package com.example.agent.intent;

import com.example.agent.llm.model.Message;

import java.util.List;
import java.util.regex.Pattern;

public class RuleBasedIntentRecognizer implements IntentRecognizer {

    private static final Pattern CODE_GENERATION_PATTERN = Pattern.compile(
            "(写|生成|创建|实现|编写|开发).*(代码|函数|方法|类|组件|模块|脚本)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CODE_MODIFICATION_PATTERN = Pattern.compile(
            "(修改|更改|更新|重构|优化|改进|修复|修正|编辑).*(代码|函数|方法|类|文件)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DEBUGGING_PATTERN = Pattern.compile(
            "(错误|异常|报错|bug|问题|崩溃|失败|不工作|不生效|调试|debug)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FILE_OPERATION_PATTERN = Pattern.compile(
            "(读取|写入|删除|创建|查看|打开|保存).*(文件|目录|文件夹)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROJECT_ANALYSIS_PATTERN = Pattern.compile(
            "(分析|了解|查看|探索|搜索|查找).*(项目|代码库|结构|架构)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CODE_REVIEW_PATTERN = Pattern.compile(
            "(审查|检查|review|优化|改进|重构).*(代码|实现)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "(什么是|如何|为什么|怎么|请问|解释|说明|介绍)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public IntentResult recognize(String userInput) {
        return recognize(userInput, null);
    }

    @Override
    public IntentResult recognize(String userInput, List<Message> context) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return IntentResult.unknown();
        }

        String input = userInput.trim();

        if (CODE_GENERATION_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.CODE_GENERATION)
                    .confidence(0.85)
                    .reasoning("匹配代码生成模式")
                    .build();
        }

        if (CODE_MODIFICATION_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.CODE_MODIFICATION)
                    .confidence(0.85)
                    .reasoning("匹配代码修改模式")
                    .build();
        }

        if (DEBUGGING_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.DEBUGGING)
                    .confidence(0.80)
                    .reasoning("匹配调试问题模式")
                    .build();
        }

        if (FILE_OPERATION_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.FILE_OPERATION)
                    .confidence(0.80)
                    .reasoning("匹配文件操作模式")
                    .build();
        }

        if (PROJECT_ANALYSIS_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.PROJECT_ANALYSIS)
                    .confidence(0.75)
                    .reasoning("匹配项目分析模式")
                    .build();
        }

        if (CODE_REVIEW_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.CODE_REVIEW)
                    .confidence(0.75)
                    .reasoning("匹配代码审查模式")
                    .build();
        }

        if (QUESTION_PATTERN.matcher(input).find()) {
            return IntentResult.builder()
                    .type(IntentType.QUESTION)
                    .confidence(0.70)
                    .reasoning("匹配一般问题模式")
                    .build();
        }

        if (containsCodeKeywords(input)) {
            return IntentResult.builder()
                    .type(IntentType.CODE_GENERATION)
                    .confidence(0.60)
                    .reasoning("包含代码相关关键词")
                    .build();
        }

        return IntentResult.builder()
                .type(IntentType.QUESTION)
                .confidence(0.50)
                .reasoning("默认归类为一般问题")
                .build();
    }

    private boolean containsCodeKeywords(String input) {
        String[] codeKeywords = {
            "代码", "函数", "方法", "类", "接口", "变量",
            "function", "class", "method", "variable", "interface",
            "实现", "编写", "开发", "编程"
        };

        String lowerInput = input.toLowerCase();
        for (String keyword : codeKeywords) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
