package com.example.agent.domain.ast;

import java.util.Collections;
import java.util.List;

public class ParseResult {

    private final boolean valid;
    private final List<SyntaxError> errors;
    private final String content;

    public ParseResult(boolean valid, List<SyntaxError> errors, String content) {
        this.valid = valid;
        this.errors = errors != null ? errors : Collections.emptyList();
        this.content = content;
    }

    public boolean isValid() {
        return valid;
    }

    public List<SyntaxError> getErrors() {
        return errors;
    }

    public String getContent() {
        return content;
    }

    public int getErrorCount() {
        return errors.size();
    }

    public String formatErrors() {
        if (valid || errors.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(errors.size(), 3); i++) {
            sb.append(errors.get(i).format()).append("\n");
        }
        if (errors.size() > 3) {
            sb.append("   ... 以及 ").append(errors.size() - 3).append(" 个更多错误\n");
        }
        return sb.toString();
    }
}
