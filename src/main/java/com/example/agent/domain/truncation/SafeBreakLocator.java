package com.example.agent.domain.truncation;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;

public class SafeBreakLocator {

    private static final int DEFAULT_SEARCH_BACK_LINES = 8;

    private static final List<Predicate<String>> SAFE_BREAK_RULES = new ArrayList<>();

    static {
        SAFE_BREAK_RULES.add(line -> line.isBlank());
        SAFE_BREAK_RULES.add(line -> line.matches("^\\s*[}\\])]\\s*;?$"));
        SAFE_BREAK_RULES.add(line -> line.trim().endsWith(";"));
        SAFE_BREAK_RULES.add(line -> line.matches("^\\s*(class|def|fn|interface|enum|struct|type)\\s+.*"));
        SAFE_BREAK_RULES.add(line -> line.matches("^\\s*(public|private|protected|static|final)\\s+.*"));
    }

    public OptionalInt findSafeBreakLine(String[] lines, int targetLine) {
        return findSafeBreakLine(lines, targetLine, DEFAULT_SEARCH_BACK_LINES);
    }

    public OptionalInt findSafeBreakLine(String[] lines, int targetLine, int searchBackLines) {
        if (targetLine <= 0) {
            return OptionalInt.of(0);
        }
        if (targetLine >= lines.length) {
            return OptionalInt.of(lines.length);
        }

        int start = Math.max(0, targetLine - searchBackLines);
        for (int i = targetLine; i >= start; i--) {
            if (isSafeBreak(lines[i])) {
                return OptionalInt.of(i + 1);
            }
        }

        for (int i = targetLine; i >= start; i--) {
            if (lines[i].isBlank()) {
                return OptionalInt.of(i + 1);
            }
        }

        return OptionalInt.empty();
    }

    private boolean isSafeBreak(String line) {
        for (Predicate<String> rule : SAFE_BREAK_RULES) {
            if (rule.test(line)) {
                return true;
            }
        }
        return false;
    }
}
