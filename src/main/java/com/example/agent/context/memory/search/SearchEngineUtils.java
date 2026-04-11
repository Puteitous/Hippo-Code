package com.example.agent.context.memory.search;

import java.nio.file.Path;

public class SearchEngineUtils {
    public static final String[] CODE_EXTENSIONS = {"java", "py", "js", "ts", "md", "xml", "yaml", "yml", "json"};
    public static final String[] IGNORE_DIRS = {"target", "build", ".git", "node_modules", ".idea"};

    public static boolean isCodeFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        for (String ext : CODE_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNotInIgnoredDir(Path path) {
        String pathStr = path.toString().toLowerCase();
        for (String dir : IGNORE_DIRS) {
            if (pathStr.contains("/" + dir + "/") || pathStr.contains("\\" + dir + "\\")) {
                return false;
            }
        }
        return true;
    }

    public static String generatePreview(String content, String[] keywords) {
        StringBuilder preview = new StringBuilder();
        String[] lines = content.split("\n");

        for (int i = 0; i < Math.min(lines.length, 150); i++) {
            String line = lines[i];
            if (line.contains("class ") || line.contains("interface ") || line.contains("public ")) {
                String lineLower = line.toLowerCase();
                for (String keyword : keywords) {
                    if (keyword.length() >= 2 && lineLower.contains(keyword)) {
                        int end = Math.min(lines.length, i + 5);
                        for (int j = i; j < end; j++) {
                            preview.append(lines[j]).append("\n");
                        }
                        preview.append("  ...\n");
                        return preview.toString();
                    }
                }
            }
        }

        for (int i = 0; i < Math.min(lines.length, 100); i++) {
            String lineLower = lines[i].toLowerCase();
            for (String keyword : keywords) {
                if (keyword.length() >= 2 && lineLower.contains(keyword)) {
                    int start = Math.max(0, i - 1);
                    int end = Math.min(lines.length, i + 4);
                    for (int j = start; j < end; j++) {
                        preview.append(lines[j]).append("\n");
                    }
                    preview.append("  ...\n");
                    return preview.toString();
                }
            }
        }

        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            preview.append(lines[i]).append("\n");
        }
        return preview.toString();
    }
}
