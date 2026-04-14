package com.example.agent.domain.index.search;

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
            String dirName = dir.toLowerCase();
            if (pathStr.contains("/" + dirName + "/")
                || pathStr.contains("\\" + dirName + "\\")
                || pathStr.startsWith(dirName + "/")
                || pathStr.startsWith(dirName + "\\")) {
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
            preview.append(lines[i]).append("\n");
        }
        if (lines.length > 100) {
            preview.append("  ... (").append(lines.length - 100).append(" more lines) ...\n");
        }
        return preview.toString();
    }

    public static double calculateSimpleScore(String content, String filePath, String[] keywords) {
        double score = 0.0;
        String contentLower = content.toLowerCase();
        String fileLower = filePath.toLowerCase();

        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;

            String k = keyword.toLowerCase();
            if (fileLower.contains(k)) {
                score += 0.5;
            }

            int count = 0;
            int idx = 0;
            while ((idx = contentLower.indexOf(k, idx)) != -1) {
                count++;
                idx += k.length();
            }
            score += Math.min(count * 0.1, 0.5);
        }

        return score;
    }
}
