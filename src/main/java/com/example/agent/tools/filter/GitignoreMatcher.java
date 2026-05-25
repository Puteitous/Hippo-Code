package com.example.agent.tools.filter;

import com.example.agent.tools.PathSecurityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class GitignoreMatcher {

    private final List<GitignorePattern> patterns = new ArrayList<>();

    public GitignoreMatcher(Path searchRoot) {
        loadGitignoreFiles(searchRoot);
    }

    private void loadGitignoreFiles(Path searchRoot) {
        Path current = searchRoot.normalize().toAbsolutePath();
        Path projectRoot = PathSecurityUtils.getProjectRoot();

        while (current != null && current.startsWith(projectRoot)) {
            Path gitignoreFile = current.resolve(".gitignore");
            if (Files.isRegularFile(gitignoreFile)) {
                try {
                    List<String> lines = Files.readAllLines(gitignoreFile);
                    for (String line : lines) {
                        parseAndAddPattern(line.trim(), current);
                    }
                } catch (IOException e) {
                    // Skip unreadable .gitignore
                }
            }
            if (current.equals(projectRoot)) {
                break;
            }
            current = current.getParent();
        }
    }

    private void parseAndAddPattern(String line, Path gitignoreDir) {
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }

        boolean negation = false;
        if (line.startsWith("!")) {
            negation = true;
            line = line.substring(1);
        }

        boolean directoryOnly = line.endsWith("/");
        if (directoryOnly) {
            line = line.substring(0, line.length() - 1);
        }

        boolean anchored = line.startsWith("/");
        if (anchored) {
            line = line.substring(1);
        }

        if (line.isEmpty()) {
            return;
        }

        String regex = convertGitignoreToRegex(line);
        patterns.add(new GitignorePattern(Pattern.compile(regex), negation, directoryOnly, anchored, gitignoreDir));
    }

    private String convertGitignoreToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        int i = 0;

        while (i < pattern.length()) {
            char c = pattern.charAt(i);

            if (c == '*' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                regex.append(".*");
                i += 2;
                if (i < pattern.length() && (pattern.charAt(i) == '/' || pattern.charAt(i) == '\\')) {
                    i++;
                }
            } else if (c == '*') {
                regex.append("[^/]*");
                i++;
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else if (c == '.') {
                regex.append("\\.");
                i++;
            } else if (c == '\\' && i + 1 < pattern.length()) {
                regex.append("\\").append(pattern.charAt(i + 1));
                i += 2;
            } else {
                regex.append(c);
                i++;
            }
        }

        return regex.toString();
    }

    public boolean isIgnored(Path path) {
        Path absolutePath = path.normalize().toAbsolutePath();
        boolean ignoredByDefault = false;

        for (GitignorePattern gp : patterns) {
            if (gp.matches(absolutePath)) {
                if (gp.negation) {
                    return false;
                }
                ignoredByDefault = true;
            }
        }

        return ignoredByDefault;
    }

    private static class GitignorePattern {
        final Pattern regex;
        final boolean negation;
        final boolean directoryOnly;
        final boolean anchored;
        final Path gitignoreDir;

        GitignorePattern(Pattern regex, boolean negation, boolean directoryOnly,
                         boolean anchored, Path gitignoreDir) {
            this.regex = regex;
            this.negation = negation;
            this.directoryOnly = directoryOnly;
            this.anchored = anchored;
            this.gitignoreDir = gitignoreDir;
        }

        boolean matches(Path absolutePath) {
            String pathStr = absolutePath.toString().replace("\\", "/");
            boolean isDir = Files.isDirectory(absolutePath);

            if (directoryOnly && !isDir) {
                return false;
            }

            String basePath = gitignoreDir.toString().replace("\\", "/");
            if (!basePath.endsWith("/")) basePath += "/";

            if (!pathStr.startsWith(basePath)) {
                return false;
            }

            String relative = pathStr.substring(basePath.length());

            if (anchored) {
                return regex.matcher(relative).matches()
                    || (isDir && regex.matcher(relative + "/").matches());
            }

            if (regex.matcher(relative).matches()) {
                return true;
            }

            if (isDir && regex.matcher(relative + "/").matches()) {
                return true;
            }

            String fileName = absolutePath.getFileName().toString();
            return regex.matcher(fileName).matches();
        }
    }
}
