package com.example.agent.domain.ast;

public class SyntaxError {

    private final int line;
    private final int column;
    private final String message;
    private final String context;

    public SyntaxError(int line, int column, String message, String context) {
        this.line = line;
        this.column = column;
        this.message = message;
        this.context = context;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    public String getContext() {
        return context;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("   第 ").append(line).append(" 行, 第 ").append(column).append(" 列");
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        if (context != null && !context.isEmpty()) {
            sb.append("\n   上下文: ").append(context);
        }
        return sb.toString();
    }
}
