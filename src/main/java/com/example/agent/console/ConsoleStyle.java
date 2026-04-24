package com.example.agent.console;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public final class ConsoleStyle {

    public static final String GREEN = "GREEN";
    public static final String CYAN = "CYAN";

    private ConsoleStyle() {
    }

    public static String apply(String color, String text) {
        if (text == null) {
            return "";
        }
        if (GREEN.equals(color)) {
            return green(text);
        } else if (CYAN.equals(color)) {
            return cyan(text);
        }
        return text;
    }

    public static String green(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                .append(text)
                .toAnsi();
    }

    public static String yellow(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append(text)
                .toAnsi();
    }

    public static String red(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                .append(text)
                .toAnsi();
    }

    public static String gray(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT | AttributedStyle.BLACK))
                .append(text)
                .toAnsi();
    }

    public static String white(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                .append(text)
                .toAnsi();
    }

    public static String cyan(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(text)
                .toAnsi();
    }

    public static String blue(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE))
                .append(text)
                .toAnsi();
    }

    public static String bold(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD)
                .append(text)
                .toAnsi();
    }

    public static String boldGreen(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.GREEN))
                .append(text)
                .toAnsi();
    }

    public static String boldYellow(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW))
                .append(text)
                .toAnsi();
    }

    public static String boldRed(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.RED))
                .append(text)
                .toAnsi();
    }

    public static String boldCyan(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                .append(text)
                .toAnsi();
    }

    public static String progressBar(double ratio, int width) {
        int filled = (int) Math.round(ratio * width);
        filled = Math.max(0, Math.min(width, filled));
        
        AttributedStringBuilder bar = new AttributedStringBuilder();
        bar.append("[");
        
        String color;
        if (ratio < 0.7) {
            color = GREEN;
        } else if (ratio < 0.85) {
            color = CYAN;
        } else if (ratio < 0.95) {
            color = "YELLOW";
        } else {
            color = "RED";
        }
        
        for (int i = 0; i < width; i++) {
            if (i < filled) {
                if (GREEN.equals(color)) {
                    bar.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                } else if (CYAN.equals(color)) {
                    bar.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
                } else if ("YELLOW".equals(color)) {
                    bar.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                } else {
                    bar.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                }
                bar.append("█");
            } else {
                bar.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT | AttributedStyle.BLACK));
                bar.append("░");
            }
        }
        
        bar.style(AttributedStyle.DEFAULT);
        bar.append("]");
        return bar.toAnsi();
    }

    public static String boldBlue(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.BOLD.foreground(AttributedStyle.BLUE))
                .append(text)
                .toAnsi();
    }

    public static String dim(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.faint())
                .append(text)
                .toAnsi();
    }

    public static String cursor() {
        return "❯ ";
    }

    public static String prompt() {
        return green("> ");
    }

    public static String thinking() {
        return gray("[Thinking...]");
    }

    public static String toolCall(String toolName, String action) {
        return yellow("[Tool: " + toolName + "] " + action);
    }

    public static String error(String message) {
        return red("Error: " + message);
    }

    public static String success(String message) {
        return green("✓ " + message);
    }

    public static String info(String message) {
        return cyan("ℹ " + message);
    }

    public static String divider() {
        return gray("─".repeat(50));
    }

    public static String userLabel() {
        return boldGreen("你");
    }

    public static String aiLabel() {
        return boldCyan("AI");
    }

    public static String conversationDivider(int round) {
        int safeRound = Math.max(0, round);
        return gray("┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄") + "\n" +
               gray("│ 第 ") + yellow(String.valueOf(safeRound)) + gray(" 轮对话 ") + gray("│") + "\n" +
               gray("┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄");
    }

    public static String conversationEnd() {
        return gray("┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄");
    }
}
