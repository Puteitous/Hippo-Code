package com.example.agent.tools.filter;

import java.nio.file.Path;
import java.util.List;

public class IgnoredExtensions {

    private static final List<String> EXTENSIONS = List.of(
        ".min.js", ".min.css",
        ".map",
        ".exe", ".dll", ".so", ".dylib",
        ".jar", ".war", ".class",
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
        ".woff", ".woff2", ".ttf", ".eot",
        ".mp3", ".mp4", ".avi", ".mov",
        ".zip", ".tar", ".gz", ".7z", ".rar"
    );

    public boolean isIgnored(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getDefaultExtensions() {
        return EXTENSIONS;
    }
}
