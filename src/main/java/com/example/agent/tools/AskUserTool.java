package com.example.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class AskUserTool implements ToolExecutor {

    private static final Scanner scanner = new Scanner(System.in);

    @Override
    public String getName() {
        return "ask_user";
    }

    @Override
    public String getDescription() {
        return "向用户提问并等待回答。用于在不确定的情况下获取用户确认或选择。" +
               "支持开放式问题和选项列表。这是实现人在回路的关键工具，" +
               "确保 Agent 在执行危险或不确定操作前征得用户同意。";
    }

    @Override
    public String getParametersSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "question": {
                        "type": "string",
                        "description": "要向用户提出的问题"
                    },
                    "options": {
                        "type": "array",
                        "description": "可选的选项列表（如果提供，用户只能选择其中一个）",
                        "items": {
                            "type": "string"
                        }
                    },
                    "allow_custom_input": {
                        "type": "boolean",
                        "description": "是否允许用户输入自定义答案（默认 true，仅在提供选项时有效）",
                        "default": true
                    }
                },
                "required": ["question"]
            }
            """;
    }

    @Override
    public List<String> getAffectedPaths(JsonNode arguments) {
        return Collections.emptyList();
    }

    @Override
    public boolean requiresFileLock() {
        return false;
    }

    @Override
    public String execute(JsonNode arguments) throws ToolExecutionException {
        if (!arguments.has("question")) {
            throw new ToolExecutionException("缺少必需参数: question");
        }

        String question = arguments.get("question").asText();
        List<String> options = new ArrayList<>();
        
        if (arguments.has("options") && arguments.get("options").isArray()) {
            ArrayNode optionsArray = (ArrayNode) arguments.get("options");
            for (JsonNode option : optionsArray) {
                options.add(option.asText());
            }
        }

        boolean allowCustomInput = !arguments.has("allow_custom_input") || 
                                    arguments.get("allow_custom_input").asBoolean();

        try {
            String answer = promptUser(question, options, allowCustomInput);
            return formatResult(question, answer);
        } catch (Exception e) {
            throw new ToolExecutionException("用户交互失败: " + e.getMessage(), e);
        }
    }

    private String promptUser(String question, List<String> options, boolean allowCustomInput) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│   Agent 需要您的确认                                      │");
        System.out.println("└─────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("问题: " + question);
        System.out.println();

        if (!options.isEmpty()) {
            System.out.println("选项:");
            for (int i = 0; i < options.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + options.get(i));
            }
            if (allowCustomInput) {
                System.out.println("  0. 输入自定义答案");
            }
            System.out.println();
            System.out.print("请选择 (输入数字");

            if (allowCustomInput) {
                System.out.print("或直接输入答案");
            }
            System.out.print("): ");

            String input = scanner.nextLine().trim();

            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= options.size()) {
                    return options.get(choice - 1);
                } else if (choice == 0 && allowCustomInput) {
                    System.out.print("请输入您的答案: ");
                    return scanner.nextLine().trim();
                } else {
                    System.out.println("无效的选择，请重新输入。");
                    return promptUser(question, options, allowCustomInput);
                }
            } catch (NumberFormatException e) {
                if (allowCustomInput) {
                    return input;
                } else {
                    System.out.println("请输入有效的数字选项。");
                    return promptUser(question, options, allowCustomInput);
                }
            }
        } else {
            System.out.print("您的回答: ");
            return scanner.nextLine().trim();
        }
    }

    private String formatResult(String question, String answer) {
        StringBuilder result = new StringBuilder();
        
        result.append("用户回答\n");
        result.append("─────────────────────────────────────────────────────────────\n");
        result.append("问题: ").append(question).append("\n");
        result.append("回答: ").append(answer).append("\n");
        result.append("─────────────────────────────────────────────────────────────\n");
        
        return result.toString();
    }
}
