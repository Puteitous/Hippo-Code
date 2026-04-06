package com.example.agent.intent;

import com.example.agent.llm.model.Message;

import java.util.ArrayList;
import java.util.Comparator;
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

    private static class IntentMatch {
        final IntentType type;
        final String reasoning;
        final int priority;

        IntentMatch(IntentType type, String reasoning, int priority) {
            this.type = type;
            this.reasoning = reasoning;
            this.priority = priority;
        }
    }

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
        List<IntentMatch> matches = new ArrayList<>();

        collectMatches(input, matches);

        if (matches.isEmpty()) {
            return IntentResult.builder()
                    .type(IntentType.QUESTION)
                    .confidence(0.50)
                    .reasoning("默认归类为一般问题")
                    .build();
        }

        if (matches.size() == 1) {
            IntentMatch match = matches.get(0);
            return IntentResult.builder()
                    .type(match.type)
                    .confidence(getConfidenceForPriority(match.priority))
                    .reasoning(match.reasoning)
                    .build();
        }

        IntentMatch best = matches.stream()
                .max(Comparator.comparingInt(m -> m.priority))
                .orElse(matches.get(0));

        return IntentResult.builder()
                .type(best.type)
                .confidence(0.50)
                .reasoning(best.reasoning + "（多模式匹配，仅供参考）")
                .build();
    }

    private void collectMatches(String input, List<IntentMatch> matches) {
        if (DEBUGGING_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.DEBUGGING, "匹配调试问题模式", 11));
        }
        if (CODE_GENERATION_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.CODE_GENERATION, "匹配代码生成模式", 10));
        }
        if (CODE_MODIFICATION_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.CODE_MODIFICATION, "匹配代码修改模式", 9));
        }
        if (QUESTION_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.QUESTION, "匹配一般问题模式", 8));
        }
        if (FILE_OPERATION_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.FILE_OPERATION, "匹配文件操作模式", 7));
        }
        if (PROJECT_ANALYSIS_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.PROJECT_ANALYSIS, "匹配项目分析模式", 6));
        }
        if (CODE_REVIEW_PATTERN.matcher(input).find()) {
            matches.add(new IntentMatch(IntentType.CODE_REVIEW, "匹配代码审查模式", 5));
        }
        if (matches.isEmpty() && containsCodeKeywords(input)) {
            matches.add(new IntentMatch(IntentType.CODE_GENERATION, "包含代码相关关键词", 4));
        }
    }

    private double getConfidenceForPriority(int priority) {
        if (priority >= 10) return 0.85;
        if (priority >= 8) return 0.75;
        if (priority >= 6) return 0.70;
        return 0.60;
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
