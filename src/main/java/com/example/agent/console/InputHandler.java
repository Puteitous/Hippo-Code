package com.example.agent.console;

import com.example.agent.console.ConsoleStyle;
import com.example.agent.service.TokenEstimator;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.Objects;

public class InputHandler {

    private static final int MAX_SINGLE_INPUT_TOKENS = 10000;
    private static final int MAX_MULTILINE_LINES = 1000;
    private static final int MAX_MULTILINE_CHARS = 100000;

    private final LineReader reader;
    private final TokenEstimator tokenEstimator;

    public InputHandler(LineReader reader, TokenEstimator tokenEstimator) {
        this.reader = Objects.requireNonNull(reader, "reader cannot be null");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator cannot be null");
    }

    public String readMultilineInput() {
        System.out.println(ConsoleStyle.boldCyan("╔══════════════════════════════════════════════════╗"));
        System.out.println(ConsoleStyle.boldCyan("║              多行输入模式                          ║"));
        System.out.println(ConsoleStyle.boldCyan("╚══════════════════════════════════════════════════╝"));
        System.out.println();
        System.out.println(ConsoleStyle.gray("输入或粘贴多行内容，单独输入 \"\"\" 结束"));
        System.out.println(ConsoleStyle.gray("或按 Ctrl+C 取消"));
        System.out.println(ConsoleStyle.gray("最大限制: " + MAX_MULTILINE_LINES + " 行, " + MAX_MULTILINE_CHARS + " 字符"));
        System.out.println();

        StringBuilder buffer = new StringBuilder();
        int lineCount = 0;

        while (true) {
            try {
                String line = reader.readLine(ConsoleStyle.yellow("... "));
                
                if (line == null || "\"\"\"".equals(line.trim())) {
                    break;
                }
                
                if (buffer.length() > 0) {
                    buffer.append("\n");
                }
                buffer.append(line);
                lineCount++;
                
                // 检查是否超过限制
                if (lineCount >= MAX_MULTILINE_LINES) {
                    System.out.println(ConsoleStyle.yellow("已达到最大行数限制 (" + MAX_MULTILINE_LINES + " 行)"));
                    break;
                }
                
                if (buffer.length() >= MAX_MULTILINE_CHARS) {
                    System.out.println(ConsoleStyle.yellow("已达到最大字符数限制 (" + MAX_MULTILINE_CHARS + " 字符)"));
                    break;
                }
                
            } catch (UserInterruptException e) {
                System.out.println(ConsoleStyle.info("已取消多行输入"));
                return null;
            } catch (EndOfFileException e) {
                break;
            }
        }

        if (buffer.length() == 0) {
            System.out.println(ConsoleStyle.yellow("输入为空，已取消"));
            return null;
        }

        System.out.println();
        System.out.println(ConsoleStyle.success("已接收 " + lineCount + " 行内容 (" + buffer.length() + " 字符)"));
        System.out.println();

        return buffer.toString();
    }

    public String handleLongInput(String input, int tokens) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (tokens <= 0) {
            return input;
        }
        
        System.out.println();
        System.out.println(ConsoleStyle.boldYellow("╔══════════════════════════════════════════════════╗"));
        System.out.println(ConsoleStyle.boldYellow("║              ⚠ 输入内容过长                        ║"));
        System.out.println(ConsoleStyle.boldYellow("╚══════════════════════════════════════════════════╝"));
        System.out.println();
        System.out.println(ConsoleStyle.yellow("当前大小: " + tokens + " tokens"));
        System.out.println(ConsoleStyle.yellow("最大限制: " + MAX_SINGLE_INPUT_TOKENS + " tokens"));
        System.out.println(ConsoleStyle.yellow("超出部分: " + (tokens - MAX_SINGLE_INPUT_TOKENS) + " tokens"));
        System.out.println();
        
        int maxChars = MAX_SINGLE_INPUT_TOKENS * 2;
        String truncated = input.substring(0, Math.min(maxChars, input.length()));
        String removed = input.length() > maxChars ? input.substring(maxChars) : "";
        
        System.out.println(ConsoleStyle.gray("── 保留部分预览 (前 200 字符) ──"));
        System.out.println(ConsoleStyle.dim(truncate(truncated, 200)));
        System.out.println();
        if (!removed.isEmpty()) {
            System.out.println(ConsoleStyle.gray("── 将被删除部分预览 (前 200 字符) ──"));
            System.out.println(ConsoleStyle.red(truncate(removed, 200)));
            System.out.println();
        }
        
        System.out.println(ConsoleStyle.cyan("请选择操作:"));
        System.out.println(ConsoleStyle.green("  [Enter] ") + ConsoleStyle.white("继续提交（截断内容）"));
        System.out.println(ConsoleStyle.green("  [E]     ") + ConsoleStyle.white("编辑输入"));
        System.out.println(ConsoleStyle.green("  [C]     ") + ConsoleStyle.white("取消本次输入"));
        System.out.println();
        
        try {
            String choice = reader.readLine(ConsoleStyle.yellow("请选择: ")).trim().toUpperCase();
            
            switch (choice) {
                case "":
                case "Y":
                    System.out.println(ConsoleStyle.success("已截断并提交"));
                    return truncated;
                case "E":
                    System.out.println(ConsoleStyle.info("请重新输入（按 Ctrl+C 取消）:"));
                    String newInput = reader.readLine(ConsoleStyle.prompt());
                    if (newInput != null && !newInput.trim().isEmpty()) {
                        int newTokens = tokenEstimator.estimateTextTokens(newInput);
                        if (newTokens > MAX_SINGLE_INPUT_TOKENS) {
                            return handleLongInput(newInput, newTokens);
                        }
                        return newInput;
                    }
                    return null;
                case "C":
                case "N":
                    System.out.println(ConsoleStyle.info("已取消"));
                    return null;
                default:
                    System.out.println(ConsoleStyle.yellow("无效选择，已取消"));
                    return null;
            }
        } catch (UserInterruptException e) {
            System.out.println(ConsoleStyle.info("已取消"));
            return null;
        } catch (EndOfFileException e) {
            System.out.println(ConsoleStyle.info("已取消"));
            return null;
        } catch (Exception e) {
            System.out.println(ConsoleStyle.info("已取消"));
            return null;
        }
    }

    public String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (maxLength <= 0) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    public int getMaxInputTokens() {
        return MAX_SINGLE_INPUT_TOKENS;
    }

    public String readLine(String prompt) throws UserInterruptException, EndOfFileException {
        return reader.readLine(prompt);
    }
}