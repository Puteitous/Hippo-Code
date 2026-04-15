package com.example.agent.progress;

import com.example.agent.console.ConsoleStyle;

import java.util.ArrayList;
import java.util.List;

public class DiffPreviewer {

    private static final int MAX_CONTEXT_LINES = 3;

    public String generateUnifiedDiff(String filePath, String oldText, String newText) {
        StringBuilder diff = new StringBuilder();

        diff.append("\n");
        diff.append(ConsoleStyle.boldYellow("⚠️  即将修改文件")).append("\n");
        diff.append(ConsoleStyle.gray("─────────────────────────────────────────────────────────────")).append("\n");
        diff.append("📄 ").append(ConsoleStyle.cyan(filePath)).append("\n");
        diff.append(ConsoleStyle.gray("─────────────────────────────────────────────────────────────")).append("\n");

        List<String> oldLines = normalizeLines(oldText.split("\n", -1));
        List<String> newLines = normalizeLines(newText.split("\n", -1));

        List<DiffHunk> hunks = computeDiffHunks(oldLines, newLines);

        for (DiffHunk hunk : hunks) {
            diff.append(renderHunk(hunk)).append("\n");
        }

        diff.append(ConsoleStyle.gray("─────────────────────────────────────────────────────────────")).append("\n");
        diff.append(formatChangeStats(oldLines, newLines)).append("\n");
        diff.append(ConsoleStyle.gray("─────────────────────────────────────────────────────────────")).append("\n");

        return diff.toString();
    }

    public String renderConfirmationPrompt() {
        return "\n" + ConsoleStyle.bold("确认执行此修改吗？") + "\n" +
                ConsoleStyle.green("  [Y] ") + ConsoleStyle.gray("接受修改") + "    " +
                ConsoleStyle.red("  [N] ") + ConsoleStyle.gray("取消操作") + "    " +
                ConsoleStyle.cyan("  [E] ") + ConsoleStyle.gray("查看详情") + "\n" +
                ConsoleStyle.yellow("\n请输入选择 (Y/n/e): ");
    }

    private List<String> normalizeLines(String[] lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(line.replace("\r", ""));
        }
        return result;
    }

    private String renderHunk(DiffHunk hunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(ConsoleStyle.gray(String.format("@@ -%d,%d +%d,%d @@",
                hunk.oldStart + 1, hunk.oldLines.size(),
                hunk.newStart + 1, hunk.newLines.size()))).append("\n");

        for (int i = 0; i < Math.max(hunk.oldLines.size(), hunk.newLines.size()); i++) {
            if (i < hunk.oldLines.size() && i < hunk.newLines.size()) {
                String oldLine = hunk.oldLines.get(i);
                String newLine = hunk.newLines.get(i);
                if (oldLine.equals(newLine)) {
                    sb.append(ConsoleStyle.gray("  " + oldLine)).append("\n");
                } else {
                    sb.append(ConsoleStyle.red("- " + oldLine)).append("\n");
                    sb.append(ConsoleStyle.green("+ " + newLine)).append("\n");
                }
            } else if (i < hunk.oldLines.size()) {
                sb.append(ConsoleStyle.red("- " + hunk.oldLines.get(i))).append("\n");
            } else {
                sb.append(ConsoleStyle.green("+ " + hunk.newLines.get(i))).append("\n");
            }
        }

        return sb.toString();
    }

    private List<DiffHunk> computeDiffHunks(List<String> oldLines, List<String> newLines) {
        List<DiffHunk> hunks = new ArrayList<>();

        DiffHunk currentHunk = new DiffHunk();
        currentHunk.oldStart = 0;
        currentHunk.newStart = 0;

        int oldIdx = 0;
        int newIdx = 0;

        while (oldIdx < oldLines.size() && newIdx < newLines.size()) {
            if (oldLines.get(oldIdx).equals(newLines.get(newIdx))) {
                if (!currentHunk.oldLines.isEmpty()) {
                    for (int ctx = 0; ctx < MAX_CONTEXT_LINES && oldIdx < oldLines.size() && newIdx < newLines.size(); ctx++) {
                        if (oldLines.get(oldIdx).equals(newLines.get(newIdx))) {
                            currentHunk.oldLines.add(oldLines.get(oldIdx));
                            currentHunk.newLines.add(newLines.get(newIdx));
                            oldIdx++;
                            newIdx++;
                        } else {
                            break;
                        }
                    }
                    hunks.add(currentHunk);
                    currentHunk = new DiffHunk();
                    currentHunk.oldStart = oldIdx;
                    currentHunk.newStart = newIdx;
                }
                oldIdx++;
                newIdx++;
            } else {
                if (currentHunk.oldLines.isEmpty()) {
                    int ctxStart = Math.max(0, oldIdx - MAX_CONTEXT_LINES);
                    currentHunk.oldStart = ctxStart;
                    currentHunk.newStart = Math.max(0, newIdx - MAX_CONTEXT_LINES);
                    for (int ctx = Math.max(0, oldIdx - MAX_CONTEXT_LINES); ctx < oldIdx; ctx++) {
                        currentHunk.oldLines.add(oldLines.get(ctx));
                    }
                    for (int ctx = Math.max(0, newIdx - MAX_CONTEXT_LINES); ctx < newIdx; ctx++) {
                        currentHunk.newLines.add(newLines.get(ctx));
                    }
                }
                currentHunk.oldLines.add(oldLines.get(oldIdx));
                currentHunk.newLines.add(newLines.get(newIdx));
                oldIdx++;
                newIdx++;
            }
        }

        while (oldIdx < oldLines.size() || newIdx < newLines.size()) {
            if (currentHunk.oldLines.isEmpty()) {
                currentHunk.oldStart = oldIdx;
                currentHunk.newStart = newIdx;
            }
            if (oldIdx < oldLines.size()) {
                currentHunk.oldLines.add(oldLines.get(oldIdx));
                oldIdx++;
            }
            if (newIdx < newLines.size()) {
                currentHunk.newLines.add(newLines.get(newIdx));
                newIdx++;
            }
        }

        if (!currentHunk.oldLines.isEmpty() || !currentHunk.newLines.isEmpty()) {
            hunks.add(currentHunk);
        }

        if (hunks.isEmpty() && !oldLines.equals(newLines)) {
            DiffHunk fallback = new DiffHunk();
            fallback.oldStart = 0;
            fallback.newStart = 0;
            fallback.oldLines = new ArrayList<>(oldLines);
            fallback.newLines = new ArrayList<>(newLines);
            hunks.add(fallback);
        }

        return hunks;
    }

    private String formatChangeStats(List<String> oldLines, List<String> newLines) {
        int added = 0;
        int removed = 0;

        for (String line : newLines) {
            if (!oldLines.contains(line)) {
                added++;
            }
        }
        for (String line : oldLines) {
            if (!newLines.contains(line)) {
                removed++;
            }
        }

        return String.format("📊 %s 行增加, %s 行删除",
                ConsoleStyle.green("+" + added),
                ConsoleStyle.red("-" + removed));
    }

    private static class DiffHunk {
        int oldStart;
        int newStart;
        List<String> oldLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();
    }
}
