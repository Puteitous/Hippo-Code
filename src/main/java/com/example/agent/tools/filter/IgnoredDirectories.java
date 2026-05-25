package com.example.agent.tools.filter;

import java.nio.file.Path;
import java.util.List;

public class IgnoredDirectories {

    private static final List<String> DIRECTORIES = List.of(
        ".git", ".svn", ".hg", ".bzr",
        "node_modules", "vendor", ".venv", "venv",
        "target", "build", "dist", "out",
        ".next", ".nuxt",
        "__pycache__", ".cache",
        ".idea", ".vscode", ".trae",
        ".gradle", "gradle",
        ".hippo", ".mvn"
    );

    public boolean isIgnored(Path path) {
        for (int i = 0; i < path.getNameCount(); i++) {
            String segment = path.getName(i).toString();
            if (DIRECTORIES.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getDefaultDirectories() {
        return DIRECTORIES;
    }
}
