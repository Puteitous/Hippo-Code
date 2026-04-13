package com.example.agent.console;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public final class ConsoleStyle {

    private ConsoleStyle() {
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

    public static String blue(String text) {
        if (text == null) {
            return "";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE))
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
